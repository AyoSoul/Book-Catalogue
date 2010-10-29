/*
 * @copyright 2010 Evan Leybourn
 * @license GNU General Public License
 * 
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 
 * This is the Administration page. It contains details about the app, links
 * to my website and email, functions to export and import books and functions to 
 * manage bookshelves.
 * 
 * @author Evan Leybourn
 */
public class AdministrationFunctions extends Activity {
	private static final int ACTIVITY_BOOKSHELF=1;
	private static final int ACTIVITY_FIELD_VISIBILITY=2;
	private CatalogueDBAdapter mDbHelper;
	private int importUpdated = 0;
	private int importCreated = 0;
	public static String filePath = Environment.getExternalStorageDirectory() + "/" + CatalogueDBAdapter.LOCATION + "/export.csv";
	public static String UTF8 = "utf8";
	public static int BUFFER_SIZE = 8192;
	private ProgressDialog pd = null;
	private int num = 0;
	private boolean finish_after = false;
	
	public static final String DOAUTO = "do_auto";
	
	final Handler handler = new Handler() {
		public void handleMessage(Message msg) {
			int total = msg.getData().getInt("total");
			String title = msg.getData().getString("title");
			if (total == 0) {
				//Toast.makeText(AdministrationFunctions.this, num + " Thumbnail Updated", Toast.LENGTH_LONG).show();
				pd.dismiss();
				if (finish_after == true) {
					finish();
				}
				//progressThread.setState(UpdateThumbnailsThread.STATE_DONE);
			} else {
				num += 1;
				pd.incrementProgressBy(1);
				if (title.length() > 21) {
					title = title.substring(0, 20) + "...";
				}
				pd.setMessage(title);
			}
		}
	};
	
	/**
	 * Called when the activity is first created. 
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		try {
			super.onCreate(savedInstanceState);
			mDbHelper = new CatalogueDBAdapter(this);
			mDbHelper.open();
			setContentView(R.layout.administration_functions);
			Bundle extras = getIntent().getExtras();
			Log.e("BC", "Admin");
			try {
				if (extras.getString(DOAUTO).equals("export")) {
					finish_after = true;
					exportData();
				//} else if (extras.getString(DOAUTO).equals("import")) {
				//	finish_after = true;
				//	importData();
				}
			} catch (NullPointerException e) {
				//do nothing
			}
			Log.e("BC", "Setup");
			setupAdmin();
		} catch (Exception e) {
			//Log.e("Book Catalogue", "Unknown Exception - BC onCreate - " + e.getMessage() );
		}
	}
	
	/**
	 * This function builds the Administration page in 4 sections. 
	 * 1. The button to goto the manage bookshelves activity
	 * 2. The button to export the database
	 * 3. The button to import the exported file into the database
	 * 4. The application version and link details
	 * 5. The link to paypal for donation
	 */
	public void setupAdmin() {
		/* Bookshelf Link */
		Log.e("BC", "Setup");
		TextView bookshelf = (TextView) findViewById(R.id.bookshelf_label);
		Log.e("BC", bookshelf.toString());
		bookshelf.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Log.e("BC", "Click");
				manageBookselves();
				return;
			}
		});
		
		/* Manage Fields Link */
		TextView fields = (TextView) findViewById(R.id.fields_label);
		fields.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				manageFields();
				return;
			}
		});
		
		/* Export Link */
		TextView export = (TextView) findViewById(R.id.export_label);
		export.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				exportData();
				return;
			}
		});
		
		/* Import Link */
		TextView imports = (TextView) findViewById(R.id.import_label);
		/* Hack to pass this into the class */
		final AdministrationFunctions pthis = this;
		imports.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// Verify - this can be a dangerous operation
				AlertDialog alertDialog = new AlertDialog.Builder(pthis).setMessage(R.string.import_alert).create();
				alertDialog.setTitle(R.string.import_data);
				alertDialog.setIcon(android.R.drawable.ic_menu_info_details);
				alertDialog.setButton(pthis.getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						importData();
						//Toast.makeText(pthis, importUpdated + " Existing, " + importCreated + " Created", Toast.LENGTH_LONG).show();
						return;
					}
				}); 
				alertDialog.setButton2(pthis.getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						//do nothing
						return;
					}
				}); 
				alertDialog.show();
				return;
			}
		});
		
		/* Export Link */
		TextView thumb = (TextView) findViewById(R.id.thumb_label);
		thumb.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// Verify - this can be a dangerous operation
				AlertDialog alertDialog = new AlertDialog.Builder(pthis).setMessage(R.string.overwrite_thumbnail).create();
				alertDialog.setTitle(R.string.update_thumbnails);
				alertDialog.setIcon(android.R.drawable.ic_menu_info_details);
				alertDialog.setButton(pthis.getResources().getString(R.string.yes), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						updateThumbnails(true);
						return;
					}
				}); 
				alertDialog.setButton2(pthis.getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						//do nothing
						return;
					}
				}); 
				alertDialog.setButton3(pthis.getResources().getString(R.string.no), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						updateThumbnails(false);
						return;
					}
				}); 
				alertDialog.show();
				return;
			}
		});
	}
	
	/**
	 * Load the Bookshelf Activity
	 */
	private void manageBookselves() {
		Intent i = new Intent(this, Bookshelf.class);
		startActivityForResult(i, ACTIVITY_BOOKSHELF);
	}
	
	/**
	 * Load the Manage Field Visibility Activity
	 */
	private void manageFields() {
		Intent i = new Intent(this, FieldVisibility.class);
		startActivityForResult(i, ACTIVITY_FIELD_VISIBILITY);
	}
	
	private class UpdateThumbnailsThread extends Thread {
		public boolean overwrite = false;
		public Cursor books = null;
		private Handler mHandler;
		
		/**
		 * @param handler
		 */
		public UpdateThumbnailsThread(Handler handler) {
			mHandler = handler;
		}
		
		private void sendMessage(int num, String title) {
			/* Send message to the handler */
			Message msg = mHandler.obtainMessage();
			Bundle b = new Bundle();
			b.putInt("total", num);
			b.putString("title", title);
			msg.setData(b);
			mHandler.sendMessage(msg);
			return;
		}

		@Override
		public void run() {
			Looper.prepare();
			startManagingCursor(books);
			int num = 0;
			try {
				while (books.moveToNext()) {
					int id = books.getInt(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ROWID));
					String isbn = books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ISBN));
					String title = books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_TITLE));
					
					num++;
					// delete any tmp thumbnails //
					try {
						File delthumb = CatalogueDBAdapter.fetchThumbnail(0);
						delthumb.delete();
					} catch (Exception e) {
						// do nothing - this is the expected behaviour 
					}
					
					File thumb = CatalogueDBAdapter.fetchThumbnail(id);
					if ((overwrite == true || !thumb.exists()) && !isbn.equals("")) {
						sendMessage(num, title);
						BookISBNSearch bis = new BookISBNSearch();
						bis.searchAmazon(isbn);
						File tmpthumb = CatalogueDBAdapter.fetchThumbnail(0);
						/* If amazon fails, try google books */
						if (!tmpthumb.exists()) {
							bis.searchGoogle(isbn);
							tmpthumb = CatalogueDBAdapter.fetchThumbnail(0);
						}
						
						/* Copy tmpthumb over realthumb */
						try {
							tmpthumb.renameTo(thumb);
						} catch (Exception e) {
							//do nothing
						}
						
					} else {
						sendMessage(num, "Skip - " + title);
					}
				}
			} catch (Exception e) {
				// do nothing
			}
			sendMessage(0, "Complete");
		}
		
	}	
	
	/**
	 * Update all (non-existent) thumbnails
	 * 
	 * There is a current limitation that restricts the search to only books with an ISBN
	 */
	private void updateThumbnails(boolean overwrite) {
		Cursor books = mDbHelper.fetchAllBooks("b." + CatalogueDBAdapter.KEY_ROWID, "All Books");
		
		pd = new ProgressDialog(AdministrationFunctions.this);
		pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		pd.setMessage("Updating...");
		pd.setCancelable(false);
		pd.setMax(books.getCount());
		pd.show();
		
		UpdateThumbnailsThread thread = new UpdateThumbnailsThread(handler);
		thread.overwrite = overwrite;
		thread.books = books;
		thread.start();
		//Toast.makeText(AdministrationFunctions.this, R.string.download_thumbs, Toast.LENGTH_LONG).show();
	}
	
	private class ExportThread extends Thread {
		public Cursor books = null;
		private Handler mHandler;
		
		/**
		 * @param handler
		 */
		public ExportThread(Handler handler) {
			mHandler = handler;
		}
		
		private void sendMessage(int num, String title) {
			/* Send message to the handler */
			Message msg = mHandler.obtainMessage();
			Bundle b = new Bundle();
			b.putInt("total", num);
			b.putString("title", title);
			msg.setData(b);
			mHandler.sendMessage(msg);
			return;
		}

		@Override
		public void run() {
			Looper.prepare();
			int num = 0;
			
			String export = 
				'"' + CatalogueDBAdapter.KEY_ROWID + "\"," + 			//0
				'"' + CatalogueDBAdapter.KEY_FAMILY_NAME + "\"," + 		//1
				'"' + CatalogueDBAdapter.KEY_GIVEN_NAMES + "\"," + 		//2
				'"' + CatalogueDBAdapter.KEY_AUTHOR + "\"," + 			//3
				'"' + CatalogueDBAdapter.KEY_TITLE + "\"," + 			//4
				'"' + CatalogueDBAdapter.KEY_ISBN + "\"," + 			//5
				'"' + CatalogueDBAdapter.KEY_PUBLISHER + "\"," + 		//6
				'"' + CatalogueDBAdapter.KEY_DATE_PUBLISHED + "\"," + 	//7
				'"' + CatalogueDBAdapter.KEY_RATING + "\"," + 			//8
				'"' + "bookshelf_id,\"" + 								//9
				'"' + CatalogueDBAdapter.KEY_BOOKSHELF + "\"," +		//10
				'"' + CatalogueDBAdapter.KEY_READ + "\"," +				//11
				'"' + CatalogueDBAdapter.KEY_SERIES + "\"," + 			//12
				'"' + CatalogueDBAdapter.KEY_SERIES_NUM + "\"," +		//13
				'"' + CatalogueDBAdapter.KEY_PAGES + "\"," + 			//14
				'"' + CatalogueDBAdapter.KEY_NOTES + "\"," + 			//15
				'"' + CatalogueDBAdapter.KEY_LIST_PRICE + "\"," + 		//16
				'"' + CatalogueDBAdapter.KEY_ANTHOLOGY+ "\"," + 		//17
				'"' + CatalogueDBAdapter.KEY_LOCATION+ "\"," + 			//18
				'"' + CatalogueDBAdapter.KEY_READ_START+ "\"," + 		//19
				'"' + CatalogueDBAdapter.KEY_READ_END+ "\"," + 			//20
				'"' + CatalogueDBAdapter.KEY_AUDIOBOOK+ "\"," + 		//21
				'"' + CatalogueDBAdapter.KEY_SIGNED+ "\"," + 			//22
				'"' + CatalogueDBAdapter.KEY_LOANED_TO+ "\"," +			//23 
				'"' + "anthology_titles," + "\"" +						//24 
				"\n";
			if (books.moveToFirst()) {
				do { 
					num++;
					long id = books.getLong(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ROWID));
					String dateString = "";
					try {
						String[] date = books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_DATE_PUBLISHED)).split("-");
						int yyyy = Integer.parseInt(date[0]);
						int mm = Integer.parseInt(date[1])+1;
						int dd = Integer.parseInt(date[2]);
						dateString = yyyy + "-" + mm + "-" + dd;
					} catch (Exception e) {
						//do nothing
					}
					String dateReadStartString = "";
					try {
						String[] date = books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_READ_START)).split("-");
						int yyyy = Integer.parseInt(date[0]);
						int mm = Integer.parseInt(date[1])+1;
						int dd = Integer.parseInt(date[2]);
						dateReadStartString = yyyy + "-" + mm + "-" + dd;
					} catch (Exception e) {
						//do nothing
					}
					String dateReadEndString = "";
					try {
						String[] date = books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_READ_END)).split("-");
						int yyyy = Integer.parseInt(date[0]);
						int mm = Integer.parseInt(date[1])+1;
						int dd = Integer.parseInt(date[2]);
						dateReadEndString = yyyy + "-" + mm + "-" + dd;
					} catch (Exception e) {
						//do nothing
					}
					String anthology = books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ANTHOLOGY));
					String anthology_titles = "";
					if (anthology.equals(CatalogueDBAdapter.ANTHOLOGY_MULTIPLE_AUTHORS + "") || anthology.equals(CatalogueDBAdapter.ANTHOLOGY_SAME_AUTHOR + "")) {
						Cursor titles = mDbHelper.fetchAnthologyTitlesByBook(id);
						if (titles.moveToFirst()) {
							do { 
								String anth_title = titles.getString(titles.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_TITLE));
								String anth_author = titles.getString(titles.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_AUTHOR));
								anthology_titles += anth_title + " * " + anth_author + "|";
							} while (titles.moveToNext()); 
						}
					}
					String title = books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_TITLE));
					//Display the selected bookshelves
					Cursor bookshelves = mDbHelper.fetchAllBookshelvesByBook(id);
					String bookshelves_id_text = "";
					String bookshelves_name_text = "";
					while (bookshelves.moveToNext()) {
						bookshelves_id_text += bookshelves.getString(bookshelves.getColumnIndex(CatalogueDBAdapter.KEY_ROWID)) + BookEditFields.BOOKSHELF_SEPERATOR;
						bookshelves_name_text += bookshelves.getString(bookshelves.getColumnIndex(CatalogueDBAdapter.KEY_BOOKSHELF)) + BookEditFields.BOOKSHELF_SEPERATOR;
					}
					bookshelves.close();
					
					String row = "";
					row += "\"" + id + "\",";
					row += "\"" + (books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_FAMILY_NAME))+"").replace("\"", "\"\"") + "\",";
					row += "\"" + (books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_GIVEN_NAMES))+"").replace("\"", "\"\"") + "\",";
					row += "\"" + (books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_AUTHOR))+"").replace("\"", "\"\"") + "\",";
					row += "\"" + (title+"").replace("\"", "\"\"") + "\",";
					row += "\"" + (books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_ISBN))+"").replace("\"", "\"\"") + "\",";
					row += "\"" + (books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_PUBLISHER))+"").replace("\"", "\"\"") + "\",";
					row += "\"" + dateString + "\",";
					row += "\"" + (books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_RATING))+"").replace("\"", "\"\"") + "\",";
					row += "\"" + bookshelves_id_text.replace("\"", "\"\"") + "\",";
					row += "\"" + bookshelves_name_text.replace("\"", "\"\"") + "\",";
					row += "\"" + (books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_READ))+"").replace("\"", "\"\"") + "\",";
					row += "\"" + (books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_SERIES))+"").replace("\"", "\"\"") + "\",";
					row += "\"" + (books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_SERIES_NUM))+"").replace("\"", "\"\"") + "\",";
					row += "\"" + (books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_PAGES))+"").replace("\"", "\"\"") + "\",";
					row += "\"" + (books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_NOTES))+"").replace("\"", "\"\"") + "\",";
					row += "\"" + (books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_LIST_PRICE))+"").replace("\"", "\"\"") + "\",";
					row += "\"" + (anthology+"").replace("\"", "\"\"") + "\",";
					row += "\"" + (books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_LOCATION))+"").replace("\"", "\"\"") + "\",";
					row += "\"" + dateReadStartString + "\",";
					row += "\"" + dateReadEndString + "\",";
					row += "\"" + (books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_AUDIOBOOK))+"").replace("\"", "\"\"") + "\",";
					row += "\"" + (books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_SIGNED))+"").replace("\"", "\"\"") + "\",";
					row += "\"" + (books.getString(books.getColumnIndexOrThrow(CatalogueDBAdapter.KEY_LOANED_TO))+"").replace("\"", "\"\"") + "\",";
					row += "\"" + (anthology_titles+"").replace("\"", "\"\"") + "\"";
					row += "\n";
					export += row;
					sendMessage(num, title);
				}
				while (books.moveToNext()); 
			} 
			
			/* write to the SDCard */
			try {
				BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePath), UTF8), BUFFER_SIZE);
				out.write(export);
				out.close();
				//Toast.makeText(this, R.string.export_complete, Toast.LENGTH_LONG).show();
			} catch (IOException e) {
				//Log.e("Book Catalogue", "Could not write to the SDCard");		
				//Toast.makeText(this, R.string.export_failed, Toast.LENGTH_LONG).show();
			}
			sendMessage(0, "Complete");
		}
	}	

	/**
	 * Export all data to a CSV file
	 * 
	 * return void
	 */
	public void exportData() {
		Cursor books = mDbHelper.exportBooks();
		pd = new ProgressDialog(AdministrationFunctions.this);
		pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		pd.setMessage("Exporting...");
		pd.setCancelable(false);
		pd.setMax(books.getCount());
		pd.show();
		
		ExportThread thread = new ExportThread(handler);
		thread.books = books;
		thread.start();
	}

	/**
	 * This program reads a text file line by line and print to the console. It uses
	 * FileOutputStream to read the file.
	 * 
	 */
	public ArrayList<String> readFile() {
		ArrayList<String> importedString = new ArrayList<String>();
		
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(filePath), UTF8),BUFFER_SIZE);
			String line = "";
			while ((line = in.readLine()) != null) {
				importedString.add(line);
			}
			in.close();
		} catch (FileNotFoundException e) {
			Toast.makeText(this, R.string.import_failed, Toast.LENGTH_LONG).show();
		} catch (IOException e) {
			Toast.makeText(this, R.string.import_failed, Toast.LENGTH_LONG).show();
		}
		return importedString;
	}
	
	private class ImportThread extends Thread {
		public ArrayList<String> export = null;
		private Handler mHandler;
		
		/**
		 * @param handler
		 */
		public ImportThread(Handler handler) {
			mHandler = handler;
		}
		
		private void sendMessage(int num, String title) {
			/* Send message to the handler */
			Message msg = mHandler.obtainMessage();
			Bundle b = new Bundle();
			b.putInt("total", num);
			b.putString("title", title);
			msg.setData(b);
			mHandler.sendMessage(msg);
			return;
		}
		
		private String[] returnRow(String row) {
			//String[] imported = row.split(",");
			Pattern pat = Pattern.compile(",(\".*?\")");
			Matcher matcher = pat.matcher(row);
			StringBuffer sb = new StringBuffer();
			while (matcher.find()){
				matcher.appendReplacement(sb,","+matcher.group(1).replaceAll(",","&comm"));
			}
			matcher.appendTail(sb);
			String[] imported = sb.toString().split(",");
			for (int i=0;i<imported.length;i++){
				imported[i] = imported[i]!=null ? imported[i].replaceAll("&comm",",") : "";
				imported[i] = imported[i].trim();
				if (imported[i].indexOf("\"") == 0) {
					imported[i] = imported[i].substring(1);
				}
				int length = imported[i].length()-1;
				if (length > -1 && imported[i].lastIndexOf("\"") == length) {
					imported[i] = imported[i].substring(0, length);
				}
				imported[i] = imported[i].trim();
			}
/*			for (int i=0; i<imported.length; i++) {
				Log.e("BC", i + " " + imported[i]);
				imported[i] = imported[i].trim();
				if (imported[i].indexOf("\"") == 0) {
					imported[i] = imported[i].substring(1);
				}
				int length = imported[i].length()-1;
				if (length > -1 && imported[i].lastIndexOf("\"") == length) {
					imported[i] = imported[i].substring(0, length);
				}
				imported[i] = imported[i].trim();
			}*/
			return imported;
		}

		@Override
		public void run() {
			Looper.prepare();
			
			int row = 1;
			int num = 0;
			/* Iterate through each imported row */
			while (row < export.size()) {
				Log.e("BC", "Row: " + row);
				num++;
				String[] imported = returnRow(export.get(row));
				row++;
				Log.e("BC", "A: " + row);
				
				/* Setup aliases for each cell*/
				Long id = null;
				try {
					id = Long.parseLong(imported[0]);
				} catch(Exception e) {
					id = Long.parseLong("0");
				}
				Log.e("BC", "1: " + row);
				
				String family = "";
				try {
					family = imported[1]; 
				} catch (Exception e) {
					// family is a compulsory field
					continue;
				}
				Log.e("BC", "2: " + row);
				String given = "";
				try {
					given = imported[2]; 
				} catch (Exception e) {
					given = "";
				}
				//String author_id = imported[3];
				
				Log.e("BC", "3: " + row);
				String title = "";
				title = imported[4]; 
				try {
					title = imported[4]; 
				} catch (Exception e) {
					//title is a compulsory field
					continue;
				}
				Log.e("BC", "4: " + row);
				
				String isbn = "";
				try {
					isbn = imported[5];
				} catch (Exception e) {
					isbn = "";
				}
				Log.e("BC", "5: " + row);
				
				String publisher = "";
				try {
					publisher = imported[6]; 
				} catch (Exception e) {
					publisher = "";
				}
				Log.e("BC", "B: " + row);
				
				String date_published = "";
				try {
					date_published = imported[7];
					String[] date = date_published.split("-");
					int yyyy = Integer.parseInt(date[0]);
					int mm = Integer.parseInt(date[1])-1;
					int dd = Integer.parseInt(date[2]);
					date_published = yyyy + "-" + mm + "-" + dd;
				} catch (Exception e) {
					date_published = "";
				}
				
				float rating = 0;
				try {
					rating = Float.valueOf(imported[8]); 
				} catch (Exception e) {
					rating = 0;
				}
				
				//String bookshelf_id = imported[9]; 
				
				String bookshelf = "";
				try {
					bookshelf = imported[10];
				} catch (Exception e) {
					bookshelf = "";
				}
				
				boolean read = false;
				try {
					read = (imported[11].equals("0")? false:true); 
				} catch (Exception e) {
					read = false;
				}
				
				String series = "";
				try {
					series = imported[12]; 
				} catch (Exception e) {
					series = "";
				}
				
				String series_num = "";
				try {
					series_num = imported[13];
				} catch (Exception e) {
					series_num = "";
				}
				
				int pages = 0;
				try {
					pages = Integer.parseInt(imported[14]); 
				} catch (Exception e) {
					pages = 0;
				}
				
				String notes = "";
				try {
					notes = imported[15];
				} catch (Exception e) {
					notes = "";
				}
				
				String list_price = "";
				try {
					list_price = imported[16];
				} catch (ArrayIndexOutOfBoundsException e) {
					list_price = "";
				}
				
				int anthology = CatalogueDBAdapter.ANTHOLOGY_NO;
				try {
					anthology = Integer.parseInt(imported[17]); 
				} catch (Exception e) {
					anthology = 0;
				}
				
				String location = "";
				try {
					location = imported[18]; 
				} catch (Exception e) {
					location = "";
				}
				Log.e("BC", "C: " + row);
				
				String read_start = "";
				try {
					read_start = imported[19];
					String[] date = date_published.split("-");
					int yyyy = Integer.parseInt(date[0]);
					int mm = Integer.parseInt(date[1])-1;
					int dd = Integer.parseInt(date[2]);
					read_start = yyyy + "-" + mm + "-" + dd;
				} catch (Exception e) {
					read_start = "";
				}
				
				String read_end = "";
				try {
					read_end = imported[20];
					String[] date = date_published.split("-");
					int yyyy = Integer.parseInt(date[0]);
					int mm = Integer.parseInt(date[1])-1;
					int dd = Integer.parseInt(date[2]);
					read_end = yyyy + "-" + mm + "-" + dd;
				} catch (Exception e) {
					read_end = "";
				}
				
				boolean audiobook = false;
				try {
					audiobook = (imported[21].equals("0")? false:true); 
				} catch (Exception e) {
					audiobook = false;
				}
				
				boolean signed = false;
				try {
					signed = (imported[22].equals("0")? false:true); 
				} catch (Exception e) {
					signed = false;
				}
				
				String loan = "";
				try {
					loan = imported[23]; 
				} catch (Exception e) {
					loan = ""; 
				}
				
				String anthology_titles = "";
				try {
					anthology_titles = imported[24]; 
				} catch (Exception e) {
					anthology_titles = ""; 
				}
				
				Log.e("BC", "D: " + row);
				String author = family + ", " + given;
				try {
					if (id == 0) {
						// Book is new. It does not exist in the current database
						Cursor book = mDbHelper.fetchBookByISBNOrCombo(isbn, family, given, title);
						int rows = book.getCount();
						if (rows != 0) {
							book.moveToFirst();
							// Its a new entry, but the ISBN exists
							id = book.getLong(0);
							book.moveToFirst();
							mDbHelper.updateBook(id, author, title, isbn, publisher, date_published, rating, bookshelf, read, series, pages, series_num, notes, list_price, anthology, location, read_start, read_end, audiobook, signed);
							importUpdated++;
						} else {
							id = mDbHelper.createBook(author, title, isbn, publisher, date_published, rating, bookshelf, read, series, pages, series_num, notes, list_price, anthology, location, read_start, read_end, audiobook, signed);
							importCreated++;
						}
					} else {
						Cursor book = mDbHelper.fetchBookById(id);
						int rows = book.getCount();
						if (rows == 0) {
							mDbHelper.createBook(id, author, title, isbn, publisher, date_published, rating, bookshelf, read, series, pages, series_num, notes, list_price, anthology, location, read_start, read_end, audiobook, signed);
							importCreated++;
						} else {
							// Book exists and should be updated if it has changed
							mDbHelper.updateBook(id, author, title, isbn, publisher, date_published, rating, bookshelf, read, series, pages, series_num, notes, list_price, anthology, location, read_start, read_end, audiobook, signed);
							importUpdated++;
						}
					}
				} catch (Exception e) {
					Log.e("BC", "Import Book (Single) Error");
					// do nothing
				}
				
				if (!loan.equals("")) {
					mDbHelper.createLoan(id, loan);
				}
				
				if (anthology == CatalogueDBAdapter.ANTHOLOGY_MULTIPLE_AUTHORS || anthology == CatalogueDBAdapter.ANTHOLOGY_SAME_AUTHOR) {
					String[] extracted_titles = anthology_titles.split("|");
					for (int j=0; j<extracted_titles.length; j++) {
						String[] author_title = extracted_titles[j].split(" * ");
						if (author_title.length > 2) {
							mDbHelper.createAnthologyTitle(id, author_title[1], author_title[0]);
						}
					}
				}
				Log.e("BC", num + " " + title);
				sendMessage(num, title);
			}
			sendMessage(0, "Complete");
		}
	}	

	
	/**
	 * Import all data from the CSV file
	 * 
	 * return void
	 */
	private void importData() {
		importUpdated = 0;
		importCreated = 0;
		ArrayList<String> export = readFile();
		
		pd = new ProgressDialog(AdministrationFunctions.this);
		pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		pd.setMessage("Importing...");
		pd.setCancelable(false);
		pd.setMax(export.size() - 1);
		pd.show();
		
		ImportThread thread = new ImportThread(handler);
		thread.export = export;
		thread.start();
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);
		switch(requestCode) {
		case ACTIVITY_BOOKSHELF:
		case ACTIVITY_FIELD_VISIBILITY:
			//do nothing (yet)
			break;
		}
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		mDbHelper.close();
	} 

}