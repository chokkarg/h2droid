package com.frankcalise.h2droid;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import android.app.ListActivity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;

public class HistoryActivity extends ListActivity {
	
	private int mUnitSystem;
	private boolean mLargeUnits;
	private List<Entry> mEntryList;
	private ContentResolver mContentResolver = null;
	private String mSelectedDate = null;
	private EntryListAdapter mAdapter = null;
	private ListView mListView;
	
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Set up main layout
        setContentView(R.layout.activity_history);
        
        mContentResolver = getContentResolver();
        mListView = getListView();
        	        
    	// Set up context menu to delete a day's worth
    	// of entries on long press
    	registerForContextMenu(mListView);
    	
    }
    
    @Override
    public void onResume() {
    	super.onResume();
    	
    	this.mUnitSystem = Settings.getUnitSystem(this);
		this.mLargeUnits = Settings.getLargeUnitsSetting(this);
		
		
		
        Bundle extras = getIntent().getExtras();
        try {
        	mSelectedDate = extras.getString("date");
        } catch (NullPointerException npe) {
        	Log.d("HISTORY", "no date chosen, show all dates with entries");
        }
        
        if (mSelectedDate == null) {
        	try {
            	mEntryList = getEntries();
            
            	mAdapter = new EntryListAdapter(mEntryList, this, false);
            	mListView.setAdapter(mAdapter);
            	            	
            	final Intent i = new Intent(this, HistoryActivity.class);
            	
            	// set on item click listener for the ListView
            	mListView.setOnItemClickListener(new OnItemClickListener() {
            		public void onItemClick(AdapterView<?> parent, View view,
            				int position, long id) {
            			// When clicked, go to new activity to display
            			// selected day's entries
            			TextView text = (TextView)view.findViewById(R.id.entry_date_textview);
            			//Log.d("ITEM_CLICK", String.format("item id = %d, %s", position, text.getText()));
            			i.putExtra("date", text.getText());
            			startActivity(i);
            		}
            	});
            } catch (NullPointerException npe) {
            	Log.e("HISTORY", "null pointer exception, could not getEntries()");
            }	
        } else {
        	// show entries related to user's selected date
        	//Log.d("HISTORY", "user chose a specific date, " + selectedDate);
            mEntryList = getEntriesFromDate(mSelectedDate);
            //Log.d("HISTORY", "List size = " + entryList.size());
            
            // add the header view for this detail list
        	// sums up the total amount
            double totalAmount = getTotalAmount(mEntryList);
    		double displayAmount = totalAmount;
    		String displayUnits = "fl oz";
    		if (mUnitSystem == Settings.UNITS_METRIC) {
    			//displayAmount /= Entry.ouncePerMililiter;
    			displayUnits = "ml";
    		}
    		
    		if (mLargeUnits) {
    			Amount currentAmount = new Amount(totalAmount, mUnitSystem);
        		displayAmount = currentAmount.getAmount();
        		displayUnits = currentAmount.getUnits();
    		}
            
            TextView tvAmount = new TextView(this);
        	tvAmount.setText(String.format("%s\nTotal: %.1f %s", mSelectedDate, displayAmount, displayUnits));
        	tvAmount.setTextSize(getResources().getDimension(R.dimen.listview_header_text_size));
        	int layoutPadding = getResources().getDimensionPixelSize(R.dimen.layout_padding);
        	tvAmount.setPadding(layoutPadding, layoutPadding, layoutPadding, layoutPadding);
        	
        	mListView.addHeaderView(tvAmount);
           
        	// set up the list adapter
        	mAdapter = new EntryListAdapter(mEntryList, this, true);
        	mListView.setAdapter(mAdapter);
        }
    	
    }
    
    private List<Entry> getEntries() {
    	List<Entry> entries = new ArrayList<Entry>();    	 // List of entries to return
    	String sortOrder = WaterProvider.KEY_DATE + " DESC"; // Date descending
    	
    	ContentResolver cr = getContentResolver();
    	
    	// Return all saved entries, grouped by date
    	Cursor c = cr.query(Uri.withAppendedPath(WaterProvider.CONTENT_URI, "group_date"),
    						null, null, null, sortOrder);
    	
    	if (c.moveToFirst()) {
    		do {
    			int id = c.getInt(WaterProvider.ID_COLUMN);
    			String date = c.getString(WaterProvider.DATE_COLUMN);
    			double metricAmount = c.getDouble(WaterProvider.AMOUNT_COLUMN);
    			boolean isNonMetric = false;    			
    			
    			Entry e = new Entry(id, date, metricAmount, isNonMetric);
    			entries.add(e);
    			
    		} while (c.moveToNext());
    	}
    	
    	c.close();
    	
    	return entries;
    }
    
    private List<Entry> getEntriesFromDate(String _date) {
    	List<Entry> entries = new ArrayList<Entry>();
    	
    	String sortOrder = WaterProvider.KEY_DATE + " DESC"; // Date descending
    	ContentResolver cr = getContentResolver();
    	
    	// Convert date to format as stored in ContentProvider
    	// to provide proper parameter for WHERE
    	SimpleDateFormat sdf = new SimpleDateFormat("EEEE, MMMM dd, yyyy");
    	String formattedDate = null;
    	try {
			Date date = sdf.parse(_date);
			sdf = new SimpleDateFormat("yyyy-MM-dd");
			formattedDate = sdf.format(date);
		} catch (ParseException e) {
			Log.e("HISTORY", "could not parse date!");
		}
		
    	String selection = "date(" + WaterProvider.KEY_DATE + ") = '" + formattedDate + "'";
    	
    	// Return all saved entries, grouped by date
    	Cursor c = cr.query(WaterProvider.CONTENT_URI,
    						null, selection, null, sortOrder);
    	
    	if (c.moveToFirst()) {
    		do {
    			int id = c.getInt(WaterProvider.ID_COLUMN);
    			String date = c.getString(WaterProvider.DATE_COLUMN);
    			double metricAmount = c.getDouble(WaterProvider.AMOUNT_COLUMN);
    			boolean isNonMetric = false;    			
    			
    			Entry e = new Entry(id, date, metricAmount, isNonMetric);
    			entries.add(e);
    			
    		} while (c.moveToNext());
    	}
    	
    	c.close();
    	
    	return entries;
    }
    
    // Sum up the list of entries to get a total amount
    private double getTotalAmount(List<Entry> _entriesList) {
    	int listSize = _entriesList.size();
    	double total = 0.0;
    	
    	for (int i = 0; i < listSize; i++) {
    		if (mUnitSystem == Settings.UNITS_US) {
    			total += _entriesList.get(i).getNonMetricAmount();
    		} else {
    			total += _entriesList.get(i).getMetricAmount();
    		}
    	}
    	
    	return total;
    }
    
    // Inflate context menu for long press
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
    								ContextMenuInfo menuInfo) {
    	super.onCreateContextMenu(menu, v, menuInfo);
    	MenuInflater inflater = getMenuInflater();
    	if (mSelectedDate == null) {
    		inflater.inflate(R.menu.history_menu, menu);
    	} else {
    		inflater.inflate(R.menu.detail_history_menu, menu);
    	}
    }
    
    // Handle long press click
    @Override
    public boolean onContextItemSelected(MenuItem item) {
    	AdapterContextMenuInfo info = (AdapterContextMenuInfo)item.getMenuInfo();
    	switch (item.getItemId()) {
    		case R.id.menu_delete_day:
    			deleteAllEntriesFromRow(info.position);
    			return true;
    		case R.id.menu_delete_single:
    			deleteSingleEntryFromRow(info.position);
    			return true;
    		case R.id.menu_delete_cancel:
    			return true;
    		default:
    			return super.onContextItemSelected(item);
    	}
    	
    }
    
    public void deleteAllEntriesFromRow(int position) {
    	Log.d("HISTORY", "row = " + position);
    	Entry e = mEntryList.get(position);
    	Log.d("HISTORY", "entry = " + e.toString());
    	
    	
    	//Date ed = new Date(e.getDate());
    	//SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    	
    	//String sortOrder = WaterProvider.KEY_DATE + " DESC LIMIT 1";
    	//String[] projection = {WaterProvider.KEY_ID};
    	String where = "date('" + e.getDate() + "') = date(" + WaterProvider.KEY_DATE + ")";
    	
//    	Cursor c = mContentResolver.query(WaterProvider.CONTENT_URI, projection, where, null, sortOrder);
    	int results = mContentResolver.delete(WaterProvider.CONTENT_URI, where, null);
    	Log.d("HISTORY", String.format("Deleted %d results", results));
//    	int results = 0;
//    	if (c.moveToFirst()) {
//    		final Uri uri = Uri.parse("content://com.frankcalise.provider.h2droid/entries/" + c.getInt(0));
//    		results = mContentResolver.delete(uri, null, null);
//    	} else {
//    		//Log.d("UNDO", "no entries from today!");
//    	}
    	
//    	c.close();
    	if (results > 0 ) {
    		mEntryList.remove(position);
    		mAdapter.notifyDataSetChanged();
    	}
    }
    
    public void deleteSingleEntryFromRow(int position) {
    	Log.d("HISTORY", "row = " + (position-1));
    	Entry e = mEntryList.get(position-1);
    	Log.d("HISTORY", "entry = " + e.toString());
    	String where = e.getId() + " = " + WaterProvider.KEY_ID;
    	int results = mContentResolver.delete(WaterProvider.CONTENT_URI, where, null);
    	Log.d("HISTORY", String.format("Deleted %d results", results));
    	if (results > 0 ) {
    		mEntryList.remove(position-1);
    		mAdapter.notifyDataSetChanged();
    	}
    	
    	/// NEED TO UPDATE HEADER VIEW HERE!!
    }
    
    
    
}