package com.onscripter.plus;

import java.io.File;
import java.io.FileNotFoundException;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnKeyListener;
import android.os.Environment;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.bugsense.trace.BugSenseHandler;

public class FolderBrowserDialogWrapper implements OnItemClickListener, OnKeyListener {
    private final LinearLayout mLayout;
    private final ListView mListView;
    private FileSystemAdapter mAdapter;
    private final Context mCtx;
    private final TextView mPathText;
    private final Button mTogglePath;
    private Dialog mDialog;

    // Files
    private File mUpperBoundFile;
    private File mCurrentInternalPath;
    private File mCurrentExternalPath;
    private static File InternalStorage;
    private static File ExternalStorage;

    public FolderBrowserDialogWrapper(Context context) {
        mCtx = context;

        // Inflate the dialog
        LayoutInflater inflater = (LayoutInflater) context.getSystemService( Context.LAYOUT_INFLATER_SERVICE);
        mLayout = (LinearLayout) inflater.inflate(R.layout.folder_browser_dialog, null);
        mListView = (ListView) mLayout.findViewById(R.id.list);
        mPathText = (TextView) mLayout.findViewById(R.id.path);
        mTogglePath = (Button) mLayout.findViewById(R.id.toggleLocation);
        mListView.setOnItemClickListener(this);
        mTogglePath.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleGotoButton();
            }
        });

        // Force dialog to max height
        WindowManager window = (WindowManager)mCtx.getSystemService(Context.WINDOW_SERVICE);
        Display display = window.getDefaultDisplay();
        mLayout.setMinimumHeight(display.getHeight());

        if (InternalStorage == null) {
            InternalStorage = Environment.getExternalStorageDirectory();
            ExternalStorage = Environment2.getExternalSDCardDirectory();
        }
    }

    public ViewGroup getDialogLayout() {
        return mLayout;
    }

    public void setDialog(Dialog dialog) {
        mDialog = dialog;
    }

    public void show(String directory) {
        if (mDialog == null) {
            throw new NullPointerException("Did not run setDialog() before showing.");
        }
        mDialog.setCancelable(false);
        mDialog.setOnKeyListener(this);
        setupDirectories(directory);
        mDialog.show();
    }

    public File getResultDirectory() {
        return mAdapter.getCurrentDirectory();
    }

    public void setupDirectories(String path) {
        if (mDialog == null) {
            throw new NullPointerException("Did not run setDialog() before showing.");
        }
        // Detect if External sdcard is available, if not then remove the sdcard button and adjust the layout
        final LinearLayout.LayoutParams textLayout = (LinearLayout.LayoutParams) mPathText.getLayoutParams();
        if (ExternalStorage != null) {
            ((View)mTogglePath.getParent()).setVisibility(View.VISIBLE);
            textLayout.weight = 6f;
        } else {
            ((View)mTogglePath.getParent()).setVisibility(View.GONE);
            textLayout.weight = 10f;
        }
        mPathText.setLayoutParams(textLayout);

        // Open default location from preference, if cannot find, then open storage
        File openDir;
        if (path != null) {
            openDir = new File(path);
            if (!openDir.exists()) {
                openDir = InternalStorage;
            }
        } else {
            openDir = InternalStorage;
        }

        // Detect where the current directory is either from internal or external storage
        if (openDir.getPath().contains(InternalStorage.getPath())) {
            mUpperBoundFile = InternalStorage;
            mCurrentExternalPath = ExternalStorage;
        } else {
            mUpperBoundFile = ExternalStorage;
            mCurrentInternalPath = InternalStorage;
            mTogglePath.setText(mCtx.getString(R.string.dialog_interal_storage_text));
        }

        try {
            mAdapter = new FileSystemAdapter(mCtx, openDir, !openDir.equals(mUpperBoundFile), true);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            mDialog.dismiss();
            BugSenseHandler.sendException(e);
            Toast.makeText(mCtx, R.string.message_cannot_find_internal_storage, Toast.LENGTH_SHORT).show();
            return;
        }

        mAdapter.bindPathToTextView(mPathText);
        mListView.setAdapter(mAdapter);
    }

    private void toggleGotoButton() {
       // Toggle between the internal and external storage
       if (mUpperBoundFile.equals(InternalStorage)) {
           mCurrentInternalPath = mAdapter.getCurrentDirectory();
           mTogglePath.setText(mCtx.getString(R.string.dialog_interal_storage_text));
           mUpperBoundFile = ExternalStorage;
           mAdapter.setCurrentDirectory(mCurrentExternalPath);
       } else {
           mCurrentExternalPath = mAdapter.getCurrentDirectory();
           mTogglePath.setText(mCtx.getString(R.string.dialog_sd_card_text));
           mUpperBoundFile = InternalStorage;
           mAdapter.setCurrentDirectory(mCurrentInternalPath);
       }

       // Remove the back list item if we are at upperbound
       mAdapter.showBackListItem(!mUpperBoundFile.equals(mAdapter.getCurrentDirectory()));
   }

    @Override
    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
        // Move up a directory if back is pressed and have not hit the folder upperlimit
        if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) {
            if (mAdapter.getCurrentDirectory().equals(mUpperBoundFile)) {
                mDialog.dismiss();
            } else {
                if (mAdapter.getCurrentDirectory().getParentFile().equals(mUpperBoundFile)) {
                    mAdapter.showBackListItem(false);
                }
                mAdapter.moveUp();
            }
        }
        return false;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
        mAdapter.setChildAsCurrent(position);
        mAdapter.showBackListItem(position > 0 || !mAdapter.getCurrentDirectory()
                .equals(mUpperBoundFile));
    }
}
