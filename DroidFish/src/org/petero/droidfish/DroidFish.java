package org.petero.droidfish;

import java.util.ArrayList;
import java.util.List;

import org.petero.droidfish.gamelogic.ChessController;
import org.petero.droidfish.gamelogic.ChessParseError;
import org.petero.droidfish.gamelogic.Move;
import org.petero.droidfish.gamelogic.Position;
import org.petero.droidfish.gamelogic.TextIO;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.ClipboardManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class DroidFish extends Activity implements GUIInterface {
	// FIXME!!! Implement "edit board"
	// FIXME!!! Add about/help window
	// FIXME!!! Should react faster in analysis mode, if possible

	private ChessBoard cb;
	private ChessController ctrl = null;
	private boolean mShowThinking;
	private int mTimeLimit;
	private GameMode gameMode;

	private TextView status;
	private ScrollView moveListScroll;
	private TextView moveList;
	private TextView thinking;

	SharedPreferences settings;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        settings = PreferenceManager.getDefaultSharedPreferences(this);
        settings.registerOnSharedPreferenceChangeListener(new OnSharedPreferenceChangeListener() {
			@Override
			public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
				readPrefs();
				ctrl.setGameMode(gameMode);
			}
		});

        setContentView(R.layout.main);
        status = (TextView)findViewById(R.id.status);
        moveListScroll = (ScrollView)findViewById(R.id.scrollView);
        moveList = (TextView)findViewById(R.id.moveList);
        thinking = (TextView)findViewById(R.id.thinking);
		cb = (ChessBoard)findViewById(R.id.chessboard);
		status.setFocusable(false);
		moveListScroll.setFocusable(false);
		moveList.setFocusable(false);
		thinking.setFocusable(false);

		ctrl = new ChessController(this);
        readPrefs();

        Typeface chessFont = Typeface.createFromAsset(getAssets(), "ChessCases.ttf");
        cb.setFont(chessFont);
        cb.setFocusable(true);
        cb.requestFocus();
        cb.setClickable(true);

        ctrl.newGame(gameMode);
        {
        	String fen = "";
        	String moves = "";
        	String numUndo = "0";
    		String tmp;
        	if (savedInstanceState != null) {
        		tmp = savedInstanceState.getString("startFEN");
        		if (tmp != null) fen = tmp;
        		tmp = savedInstanceState.getString("moves");
        		if (tmp != null) moves = tmp;
        		tmp = savedInstanceState.getString("numUndo");
        		if (tmp != null) numUndo = tmp;
        	} else {
        		tmp = settings.getString("startFEN", null);
        		if (tmp != null) fen = tmp;
        		tmp = settings.getString("moves", null);
        		if (tmp != null) moves = tmp;
        		tmp = settings.getString("numUndo", null);
        		if (tmp != null) numUndo = tmp;
        	}
        	List<String> posHistStr = new ArrayList<String>();
        	posHistStr.add(fen);
        	posHistStr.add(moves);
        	posHistStr.add(numUndo);
        	ctrl.setPosHistory(posHistStr);
        }
        ctrl.startGame();
        
        cb.setOnTouchListener(new OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
		        if (ctrl.humansTurn() && (event.getAction() == MotionEvent.ACTION_UP)) {
		            int sq = cb.eventToSquare(event);
		            Move m = cb.mousePressed(sq);
		            if (m != null) {
		                ctrl.makeHumanMove(m);
		            }
		            return false;
		        }
		        return false;
			}
		});
        
        cb.setOnTrackballListener(new ChessBoard.OnTrackballListener() {
        	public void onTrackballEvent(MotionEvent event) {
		        if (ctrl.humansTurn()) {
		        	Move m = cb.handleTrackballEvent(event);
		        	if (m != null) {
		        		ctrl.makeHumanMove(m);
		        	}
		        }
        	}
        });
        cb.setOnLongClickListener(new OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
				showDialog(CLIPBOARD_DIALOG);
				return true;
			}
		});
    }

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		if (ctrl != null) {
			List<String> posHistStr = ctrl.getPosHistory();
			outState.putString("startFEN", posHistStr.get(0));
			outState.putString("moves", posHistStr.get(1));
			outState.putString("numUndo", posHistStr.get(2));
		}
	}
	
	@Override
	protected void onPause() {
		if (ctrl != null) {
			List<String> posHistStr = ctrl.getPosHistory();
			Editor editor = settings.edit();
			editor.putString("startFEN", posHistStr.get(0));
			editor.putString("moves", posHistStr.get(1));
			editor.putString("numUndo", posHistStr.get(2));
			editor.commit();
		}
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		if (ctrl != null) {
			ctrl.shutdownEngine();
		}
		super.onDestroy();
	}

	private void readPrefs() {
		String gameModeStr = settings.getString("gameMode", "1");
        int modeNr = Integer.parseInt(gameModeStr);
        gameMode = new GameMode(modeNr);
        mShowThinking = settings.getBoolean("showThinking", false);
        String timeLimitStr = settings.getString("timeLimit", "5000");
        mTimeLimit = Integer.parseInt(timeLimitStr);
        boolean boardFlipped = settings.getBoolean("boardFlipped", false);
        cb.setFlipped(boardFlipped);
        ctrl.setTimeLimit();
        String fontSizeStr = settings.getString("fontSize", "12");
        int fontSize = Integer.parseInt(fontSizeStr);
        status.setTextSize(fontSize);
        moveList.setTextSize(fontSize);
        thinking.setTextSize(fontSize);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.options_menu, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.item_new_game:
	        ctrl.newGame(gameMode);
	        ctrl.startGame();
			return true;
		case R.id.item_undo:
			ctrl.takeBackMove();
			return true;
		case R.id.item_redo:
			ctrl.redoMove();
			return true;
		case R.id.item_settings:
		{
			Intent i = new Intent(DroidFish.this, Preferences.class);
			startActivityForResult(i, 0);
			return true;
		}
		case R.id.item_about:
        	showDialog(ABOUT_DIALOG);
        	return true;
		}
		return false;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == 0) {
			readPrefs();
			ctrl.setGameMode(gameMode);
		}
	}

	@Override
	public void setPosition(Position pos) {
		cb.setPosition(pos);
	}

	@Override
	public void setSelection(int sq) {
		cb.setSelection(sq);
	}

	@Override
	public void setStatusString(String str) {
		status.setText(str);
	}

	@Override
	public void setMoveListString(String str) {
		moveList.setText(str);
		moveListScroll.fullScroll(ScrollView.FOCUS_DOWN);
	}
	
	@Override
	public void setThinkingString(String str) {
		thinking.setText(str);
	}

	@Override
	public int timeLimit() {
		return mTimeLimit;
	}

	@Override
	public boolean randomMode() {
		return mTimeLimit == -1;
	}

	@Override
	public boolean showThinking() {
		return mShowThinking || gameMode.analysisMode();
	}

	static final int PROMOTE_DIALOG = 0; 
	static final int CLIPBOARD_DIALOG = 1;
	static final int ABOUT_DIALOG = 2;
	
	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case PROMOTE_DIALOG: {
			final CharSequence[] items = {"Queen", "Rook", "Bishop", "Knight"};
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle("Promote pawn to?");
			builder.setItems(items, new DialogInterface.OnClickListener() {
			    public void onClick(DialogInterface dialog, int item) {
	        		ctrl.reportPromotePiece(item);
			    }
			});
			AlertDialog alert = builder.create();
			return alert;
		}
		case CLIPBOARD_DIALOG: {
			final CharSequence[] items = {"Copy Game", "Copy Position", "Paste"};
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle("Clipboard");
			builder.setItems(items, new DialogInterface.OnClickListener() {
			    public void onClick(DialogInterface dialog, int item) {
					switch (item) {
					case 0: {
						String pgn = ctrl.getPGN();
						ClipboardManager clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
						clipboard.setText(pgn);
						break;
					}
					case 1: {
						String fen = ctrl.getFEN() + "\n";
						ClipboardManager clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
						clipboard.setText(fen);
						break;
					}
					case 2: {
						ClipboardManager clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
						if (clipboard.hasText()) {
							String fenPgn = clipboard.getText().toString();
							try {
								ctrl.setFENOrPGN(fenPgn);
							} catch (ChessParseError e) {
								Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
							}
						}
						break;
					}
					}
			    }
			});
			AlertDialog alert = builder.create();
			return alert;
		}
		case ABOUT_DIALOG: {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle("DroidFish").setMessage(R.string.about_info);
			AlertDialog alert = builder.create();
			return alert;
		}
		}
		return null;
	}

	@Override
	public void requestPromotePiece() {
		runOnUIThread(new Runnable() {
            public void run() {
            	showDialog(PROMOTE_DIALOG);
            }
		});
	}

	@Override
	public void reportInvalidMove(Move m) {
		String msg = String.format("Invalid move %s-%s", TextIO.squareToString(m.from), TextIO.squareToString(m.to));
		Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
	}

	@Override
	public void runOnUIThread(Runnable runnable) {
		runOnUiThread(runnable);
	}
}
