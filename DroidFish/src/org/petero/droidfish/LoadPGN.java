package org.petero.droidfish;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Vector;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnDismissListener;
import android.os.Bundle;

public class LoadPGN extends Activity {
	private static final class GameInfo {
		String event = "";
		String site = "";
		String date = "";
		String round = "";
		String white = "";
		String black = "";
		String result = "";
		long startPos;
		long endPos; // -1 means to end of file
	}

	static Vector<GameInfo> gamesInFile = new Vector<GameInfo>();
	String fileName;
	ProgressDialog progress;
	static int defaultItem = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Intent i = getIntent();
		fileName = i.getAction();
		showDialog(PROGRESS_DIALOG);
		new Thread(new Runnable() {
			public void run() {
				readFile();
				runOnUiThread(new Runnable() {
					public void run() {
						progress.dismiss();
						removeDialog(SELECT_GAME_DIALOG);
						showDialog(SELECT_GAME_DIALOG);
					}
				});
			}
		}).start();
	}

	final static int PROGRESS_DIALOG = 0;
	final static int SELECT_GAME_DIALOG = 1;

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case PROGRESS_DIALOG:
			progress = new ProgressDialog(this);
			progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progress.setTitle("Parsing PGN file");
			progress.setMessage("Please wait...");
			progress.setCancelable(false);
			return progress;
		case SELECT_GAME_DIALOG:
	    	final String[] items = new String[gamesInFile.size()];
	    	for (int i = 0; i < items.length; i++) {
	    		GameInfo gi = gamesInFile.get(i);
	    		StringBuilder info = new StringBuilder(128);
	    		info.append(gi.white);
	    		info.append(" - ");
	    		info.append(gi.black);
	    		if (gi.date.length() > 0) {
	    			info.append(' ');
	    			info.append(gi.date);
	    		}
	    		if (gi.round.length() > 0) {
	    			info.append(' ');
		    		info.append(gi.round);
	    		}
	    		if (gi.event.length() > 0) {
	    			info.append(' ');
	    			info.append(gi.event);
	    		}
	    		if (gi.site.length() > 0) {
	    			info.append(' ');
	    			info.append(gi.site);
	    		}
	    		info.append(' ');
	    		info.append(gi.result);
	    		items[i] = info.toString();
	    	}
	    	if (defaultItem >= items.length) {
	    		defaultItem = 0;
	    	}
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(R.string.select_pgn_file);
			builder.setSingleChoiceItems(items, defaultItem, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int item) {
					defaultItem = item;
					sendBackResult(item);
				}
			});
			AlertDialog alert = builder.create();
			alert.setOnDismissListener(new OnDismissListener() {
				public void onDismiss(DialogInterface dialog) {
					sendBackResult(-1);
				}
			});
			return alert;
		default:
			return null;
		}
	}

	static long lastModTime = -1;
	static String lastFileName = "";
	
	private final void readFile() {
		if (!fileName.equals(lastFileName))
			defaultItem = 0;
		long modTime = new File(fileName).lastModified();
		if ((modTime == lastModTime) && fileName.equals(lastFileName))
			return;
		lastModTime = modTime;
		lastFileName = fileName;
		try {
			gamesInFile.clear();
			RandomAccessFile f = new RandomAccessFile(fileName, "r");
			long fileLen = f.length();
			GameInfo gi = null;
			boolean inHeader = false;
			long filePos = 0;
			while (true) {
				filePos = f.getFilePointer();
				String line = f.readLine();
				if (line == null)
					break; // EOF
				int len = line.length();
				if (len == 0)
					continue;
				if (line.charAt(0) == '[') {
					if (!inHeader) { // Start of game
						inHeader = true;
						if (gi != null) {
							gi.endPos = filePos;
							gamesInFile.add(gi);
							final int percent = (int)(filePos * 100 / fileLen);
							runOnUiThread(new Runnable() {
								public void run() {
									progress.setProgress(percent);
								}
							});
						}
						gi = new GameInfo();
						gi.startPos = filePos;
						gi.endPos = -1;
					}
					if (line.startsWith("[Event ")) {
						gi.event = line.substring(8, len - 2);
						if (gi.event.equals("?")) gi.event = "";
					} else if (line.startsWith("[Site ")) {
						gi.site = line.substring(7, len - 2);
						if (gi.site.equals("?")) gi.site= "";
					} else if (line.startsWith("[Date ")) {
						gi.date = line.substring(7, len - 2);
						if (gi.date.equals("?")) gi.date= "";
					} else if (line.startsWith("[Round ")) {
						gi.round = line.substring(8, len - 2);
						if (gi.round.equals("?")) gi.round= "";
					} else if (line.startsWith("[White ")) {
						gi.white = line.substring(8, len - 2);
					} else if (line.startsWith("[Black ")) {
						gi.black = line.substring(8, len - 2);
					} else if (line.startsWith("[Result ")) {
						gi.result = line.substring(9, len - 2);
					}
				} else {
					inHeader = false;
				}
			}
			if (gi != null) {
				gi.endPos = filePos;
				gamesInFile.add(gi);
			}
			f.close();
		} catch (FileNotFoundException e) {
		} catch (IOException e) {
		}
	}

	private final void sendBackResult(int gameNo) {
		try {
			if ((gameNo >= 0) && (gameNo < gamesInFile.size())) {
				GameInfo gi = gamesInFile.get(gameNo);
				RandomAccessFile f;
				f = new RandomAccessFile(fileName, "r");
				byte[] pgnData = new byte[(int) (gi.endPos - gi.startPos)];
				f.seek(gi.startPos);
				f.readFully(pgnData);
				String result = new String(pgnData);
				setResult(RESULT_OK, (new Intent()).setAction(result));
				finish();
			}
		} catch (FileNotFoundException e) {
		} catch (IOException e) {
		}
		setResult(RESULT_CANCELED);
		finish();
	}
}