package com.rebellos.sdkmanager;

import android.annotation.*;
import android.app.*;
import android.content.*;
import android.database.*;
import android.net.*;
import android.os.*;
import android.text.method.*;
import android.view.*;
import android.widget.*;
import android.app.DownloadManager.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;

public class DisplayActivity extends Activity
{
	private TextView mInfoText;
	private ProgressBar mProgress;
	private ListView mPackageList;
	private List<SDKPackage> mPackages;
	public DownloadManager dm;
	private SDKPackage mSelectedPackage;
	public SDKArchive enqueuedArchive;
	public boolean isDownloading = false;
	@SuppressLint("NewApi")
    @Override
    protected void onCreate(Bundle savedInstanceState)
	{
        super.onCreate(savedInstanceState);
        setContentView(R.layout.display);

        // Make sure we're running on Honeycomb or higher to use ActionBar APIs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
		{
            // Show the Up button in the action bar.
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }
		mInfoText = (TextView) findViewById(R.id.info_text);
		mProgress = (ProgressBar) findViewById(R.id.marker_progress);
		mPackageList = (ListView) findViewById(R.id.package_list);
		registerForContextMenu(mPackageList);
		mPackageList.setOnItemClickListener(new AdapterView.OnItemClickListener()
			{
				@Override
				public void onItemClick(AdapterView<?> adapter, View view, int position, long arg)
				{
					mSelectedPackage = (SDKPackage) mPackageList.getItemAtPosition(position);
					openContextMenu(view);
				}
			});
		mPackages = new ArrayList<SDKPackage>();
		mInfoText.setMovementMethod(new ScrollingMovementMethod());
		try
		{
			Intent intent = getIntent();
			String urlString = intent.getStringExtra(MainActivity.URL_EXTRA);
			new FetchTask().execute(urlString);
			dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
			BroadcastReceiver receiver = new BroadcastReceiver() {
				@Override
				public void onReceive(Context context, Intent intent)
				{
					String action = intent.getAction();
					if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action))
					{
						long downloadId = intent.getLongExtra(
                            DownloadManager.EXTRA_DOWNLOAD_ID, 0);
						Query query = new Query();
						query.setFilterById(enqueuedArchive.enqueue);
						Cursor c = dm.query(query);
						if (c.moveToFirst())
						{
							int columnIndex = c
                                .getColumnIndex(DownloadManager.COLUMN_STATUS);
							if (DownloadManager.STATUS_SUCCESSFUL == c
                                .getInt(columnIndex))
							{
								Toast.makeText(mPackageList.getContext(), "Succeed " + c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)), Toast.LENGTH_LONG).show();
								isDownloading = false;
							}
						}
					}
				}
			};

			registerReceiver(receiver, new IntentFilter(
								 DownloadManager.ACTION_DOWNLOAD_COMPLETE));

		}
		catch (Exception e)
		{
			e.printStackTrace();
			mInfoText.setText("Error1:" + e.toString());
			mProgress.setVisibility(View.GONE);
		}

    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo)
	{
		mSelectedPackage.inflateMenu(menu);
        super.onCreateContextMenu(menu, v, menuInfo);
    } 

    @Override
    public boolean onContextItemSelected(MenuItem item)
	{
		return mSelectedPackage.onMenuClick(this, item);
	}


	private class FetchTask extends AsyncTask<String, Void, String>
	{

        @Override
        protected String doInBackground(String... urls)
		{
			mPackages.clear();
			String result = "";
			String[] manifests = {"addon.xml", "repository-7.xml"};
            // params comes from the execute() call: params[0] is the url.
			for (String manifest : manifests)
			{
				try
				{
					URL url = new URL(urls[0] + manifest);
					HttpURLConnection conn = (HttpURLConnection) url.openConnection();
					try
					{
						InputStream in = new BufferedInputStream(conn.getInputStream());
						DocumentBuilderFactory bfactory = DocumentBuilderFactory.newInstance();
						DocumentBuilder builder = bfactory.newDocumentBuilder();
						Document doc = builder.parse(in);

						result += "Fetched " + manifest + "\n";
						NodeList nodes = doc.getElementsByTagName("*");
						for (int i = 0; i < nodes.getLength(); i++)
						{
							Node child = nodes.item(i);

							if (child.getNodeName().equals("sdk:add-on")
								|| child.getNodeName().equals("sdk:platform")
								|| child.getNodeName().equals("sdk:extra"))
							{
								mPackages.add(new SDKPackage(child, mPackageList));
							}


						}
					}
					catch (Exception e)
					{
						e.printStackTrace();
						result += "Exception " + e.toString() + " on " + manifest + "\n";
					}
					finally
					{
						conn.disconnect();
					}
				}
				catch (Exception e)
				{
					e.printStackTrace();
					result += "Exception " + e.toString() + " on " + manifest + "\n";
				}
			}
			return result;
        }
		@Override
		protected void onPreExecute()
		{
			mInfoText.setText("Fetching data...");
		}
        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(String result)
		{
			mInfoText.setText(result);
			mPackageList.setAdapter(new PackagesAdapter(DisplayActivity.this, mPackages));
			mProgress.setVisibility(View.GONE);
		}
    }

	public void updateList()
	{
		((PackagesAdapter) mPackageList.getAdapter()).notifyDataSetChanged();
	}
}
