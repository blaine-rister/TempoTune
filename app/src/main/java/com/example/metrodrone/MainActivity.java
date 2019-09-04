package com.example.metrodrone;

import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

import android.os.Bundle;

import android.content.Intent;
import android.content.ComponentName;
import android.content.ServiceConnection;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.LayoutInflater;
import android.view.inputmethod.InputMethodManager;

import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Spinner;
import android.widget.SeekBar;
import android.widget.Button;

import android.content.Context;

import android.os.IBinder;
import com.example.metrodrone.DroneService.DroneBinder;

import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;

public class MainActivity extends AppCompatActivity {

    // Constants
    final static boolean testAds = BuildConfig.DEBUG;
    final static String logTag = "metrodrone";
    final static String displaySharpsKey = "DISPLAY_SHARPS";

    // Dynamic UI items
    List<NoteSelector> noteSelectors = new ArrayList<>(); // Stores the pitches to be played

    // State
    boolean isTapping = false;
    boolean displaySharps;
    TempoTapper tempoTapper;

    // Persistent UI items
    TextView bpmTextView;

    // Ads
    InterstitialAd interstitialAd;

    // Service interface
    DroneBinder droneBinder;
    private ServiceConnection droneConnection = new ServiceConnection()
    {
        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            droneBinder = (DroneBinder) service;
            setupUI();
            updateUI();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            throw BuildConfig.DEBUG ? new DebugException("Lost connection to the server.") :
                    new DefaultException();
        }

        @Override
        public void onNullBinding(ComponentName name) {
            throw BuildConfig.DEBUG  ? new DebugException("The server returned null on binding.") :
                    new DefaultException();
        }

        @Override
        public void onBindingDied(ComponentName name) {
            throw BuildConfig.DEBUG ? new DebugException("The server binding died.") :
                    new DefaultException();
        }
    };

    // Save the UI state
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putBoolean(displaySharpsKey, displaySharps);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // If the activity is being re-created, restore the previous UI state
        final boolean restoreState = savedInstanceState != null;
        displaySharps = restoreState ? savedInstanceState.getBoolean(displaySharpsKey) : false;

        // Start the drone service and bind to it. When bound, we will set up the UI.
        Intent intent = new Intent(this, DroneService.class);
        if (startService(intent) == null) {
            throw BuildConfig.DEBUG ? new DebugException("Failed to start the service.") :
                    new DefaultException();
        }
        if (!bindService(intent, droneConnection, Context.BIND_AUTO_CREATE)) {
            throw BuildConfig.DEBUG ? new DebugException("Binding to the server returned false.") :
                    new DefaultException();
        }

        // Start drawing the layout in the meantime, but don't initialize the behavior
        setContentView(R.layout.activity_main);

        // Initialize ads
        MobileAds.initialize(this);

        // Get the ad unit IDs
        final int bannerAdUnitIdRes = testAds ? R.string.test_banner_ad_unit_id :
                R.string.real_banner_ad_unit_id;
        final int interstitialAdUnitRes = testAds ? R.string.test_interstitial_ad_unit_id :
                R.string.real_interstitial_ad_unit_id;

        // Initialize the banner ad
        AdView banner = findViewById(R.id.adView);
        banner.loadAd(new AdRequest.Builder().build());

        // Initialize the interstitial ad
        interstitialAd = new InterstitialAd(this);
        interstitialAd.setAdUnitId(getResources().getString(R.string.interstitial_ad_unit_id));
    }


    // Set the behavior of the UI components. Called after the service is bound.
    protected void setupUI() {

        // Set up the toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Play button
        ImageButton playButton = findViewById(R.id.playButton);
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                play();
            }
        });

        // Pause button
        ImageButton pauseButton = findViewById(R.id.pauseButton);
        pauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pause();
            }
        });

        // BPM text
        EditText editBpm = findViewById(R.id.editBpm);
        bpmTextView = editBpm;
        editBpm.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                switch (id) {
                    case EditorInfo.IME_ACTION_DONE:
                        // Read the text and update BPM, closing the keyboard
                        sendDisplayedBpm(textView);

                        /* Return false to close the keyboard, in case "done" is actually working.
                         * Returning true does not seem to prevent "enter" from jittering the
                         * screen. */
                        return false;
                    default:
                        return false;
                }
            }
        });

        // BPM increase button
        ImageButton bpmIncreaseButton = findViewById(R.id.bpmIncreaseButton);
        bpmIncreaseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                droneBinder.setBpm(droneBinder.getBpm() + 1);
                updateUI();
            }
        });

        // BPM decrease button
        ImageButton bpmDecreaseButton = findViewById(R.id.bpmDecreaseButton);
        bpmDecreaseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                droneBinder.setBpm(droneBinder.getBpm() - 1);
                updateUI();
            }
        });

        // Create the pitch adapter
        final ArrayAdapter<NameValPair> pitchAdapter = new ArrayAdapter<>(this,
                R.layout.pitch_spinner_item, getPitchChoices(displaySharps));
        pitchAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // Toggle sharps/flats switch
        Switch sharpSwitch = findViewById(R.id.sharpSwitch);
        sharpSwitch.setChecked(displaySharps);
        sharpSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                displaySharps = isChecked;
                pitchAdapter.clear();
                pitchAdapter.addAll(getPitchChoices(isChecked));
            }
        });

        // Add the existing notes to the UI
        final List<Integer> handles = droneBinder.getNoteHandles();
        for (Iterator<Integer> it = handles.iterator(); it.hasNext(); ) {
            addNote(it.next(), pitchAdapter);
        }

        // Add note button
        final Button addNoteButton = findViewById(R.id.addPitchButton);
        addNoteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                // Check if we have reached the note limit
                if (droneBinder.notesFull())
                    return;

                addNote(droneBinder.addNote(), pitchAdapter);
            }
        });

        // Remove pitch button
        Button removeNoteButton = findViewById(R.id.removePitchButton);
        removeNoteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                // Do nothing if there are no pitches left to remove
                if (noteSelectors.isEmpty())
                    return;

                // Get the last note
                final int removeIdx = noteSelectors.size() - 1;
                NoteSelector noteToRemove = noteSelectors.get(removeIdx);

                // Remove the note from the sounding pitches
                noteToRemove.destroy();
                noteSelectors.remove(removeIdx);

                // Remove the note from the UI layout
                LinearLayout layout = findViewById(R.id.pitchLayout);
                layout.removeView(noteToRemove.octaveSpinner);
                layout.removeView(noteToRemove.pitchSpinner);
            }
        });

        // Velocity bar
        SeekBar velocitySeekBar = findViewById(R.id.velocitySeekBar);
        velocitySeekBar.setProgress((int) Math.floor(droneBinder.getVelocity() *
                velocitySeekBar.getMax()));
        velocitySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Do nothing
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Do nothing
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Update the drone with whatever changes were made
                droneBinder.setVelocity((double) seekBar.getProgress() / seekBar.getMax()); // Assumes min is 0,
                // to check this requires higher API level
            }
        });

        // Duration bar
        SeekBar durationSeekBar = findViewById(R.id.durationSeekBar);
        durationSeekBar.setProgress((int) Math.floor(droneBinder.getDuration() *
                durationSeekBar.getMax()));
        durationSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Do nothing
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Do nothing
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                droneBinder.setDuration((double) seekBar.getProgress() / seekBar.getMax()); // Assumes min is 0,
                // to check this requires higher API level
            }
        });

        // Load the families from the CSV file
        List<NameValPair> families = readCsv(R.raw.families);

        // Initialize the instruments by querying the soundfont
        List<NameValPair> instruments = new ArrayList<>();
        for (int programNum = 0; programNum < DroneService.programMax; programNum++) {
            if (droneBinder.queryProgram(programNum)) {
                instruments.add(new NameValPair(
                        droneBinder.getProgramName(programNum),
                        programNum + 1
                ));
            } else if (BuildConfig.DEBUG) {
                Log.w(logTag, String.format("setupUI: skipping invalid program code %d",
                        programNum));
            }
        }

        // We need to have at least one instrument
        if (instruments.isEmpty())
            throw BuildConfig.DEBUG ? new DebugException("No valid instruments found!") :
                    new DefaultException();

        // Sort the lists by their codes
        Collections.sort(instruments);
        Collections.sort(families);

        // Pad the families array with a 'null' bookend which is greater than all the instruments
        families.add(new NameValPair(null, instruments.get(instruments.size() - 1).i + 1));

        // Group the instruments into families
        final List<List<NameValPair>> groupedInstruments = new ArrayList<>();
        List<String> familyNames = new ArrayList<>();
        int familyIdx = -1;
        for (Iterator<NameValPair> it = instruments.iterator(); it.hasNext();) {
            // Get the current item
            final NameValPair instrument = it.next();

            // Seek forward to the family containing this item
            boolean newFamily = false;
            for (NameValPair nextFamily = families.get(familyIdx + 1);
                 instrument.i >= nextFamily.i;
                 nextFamily = families.get(familyIdx + 1)) {
                familyIdx++;
                newFamily = true;
            }

            // Check if we need to add space for a new family
            if (newFamily) {
                groupedInstruments.add(new ArrayList<NameValPair>());
                familyNames.add(families.get(familyIdx).s);
            }

            // Add this item to the current sub-list, which is the most recent one
            List<NameValPair> familyGroup = groupedInstruments.get(groupedInstruments.size() - 1);
            familyGroup.add(instrument);
        }

        // Remove the family padding, to avoid trouble
        families.remove(families.size() - 1);

        // Instrument name spinner
        final Spinner instrumentSpinner = findViewById(R.id.instrumentNameSpinner);
        final ArrayAdapter<NameValPair> instAdapter = new ArrayAdapter<>(this,
                R.layout.instrument_spinner_item);
        instAdapter.setDropDownViewResource(android.R.layout.simple_spinner_item);
        instrumentSpinner.setAdapter(instAdapter);

        // Instrument family spinner
        Spinner familySpinner = findViewById(R.id.instrumentFamilySpinner);
        ArrayAdapter<String> familyAdapter = new ArrayAdapter<>(this,
                R.layout.instrument_spinner_item, familyNames);
        familyAdapter.setDropDownViewResource(R.layout.instrument_spinner_item);
        familySpinner.setAdapter(familyAdapter);

        // Set the spinners to reflect the current program
        // Note: must do this before installing listeners, to avoid triggering them
        updateInstrumentSpinners(groupedInstruments, familySpinner, instrumentSpinner);

        // Install the instrument spinner listener
        instrumentSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
                // Change the instrument program
                setInstrument((NameValPair) adapterView.getItemAtPosition(pos));
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                // Do nothing
            }
        });

        // Install the family spinner listener
        familySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {

                // Change the instrument choices
                setInstrumentChoices(instAdapter, groupedInstruments.get(pos));

                // Update the instrument. Don't rely on the spinner to do it, this is unreliable
                instrumentSpinner.setSelection(0, true);
                setInstrument((NameValPair) instrumentSpinner.getSelectedItem());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                // Do nothing
            }
        });

        // Tempo tapper
        FloatingActionButton tempoTapperFab = findViewById(R.id.tempoTapperFab);
        tempoTapperFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isTapping) {
                    tempoTapper.tap();
                } else {
                    //TODO could animate the button to indicate tapping mode
                    tempoTapper = new TempoTapper(view) {
                        @Override
                        public void onComplete(int bpm) {
                            isTapping = false;
                            droneBinder.setBpm(bpm);
                            updateUI();
                        }

                        @Override
                        public void onCancel() {
                            // TODO animate button
                            isTapping = false;
                        }
                    };
                    isTapping = true;
                }
            }
        });
    }

    // Set the instrument choices to the given list
    private void setInstrumentChoices(ArrayAdapter<NameValPair> adapter,
                                      List<NameValPair> instruments) {
        adapter.clear();
        adapter.addAll(instruments);
    }

    // Set the instrument spinners to the current program
    private void updateInstrumentSpinners(List<List<NameValPair>> groups, Spinner familySpinner,
                                       Spinner instrumentSpinner){

        // Look for the current program in the instrument groups. If found, set the spinners
        final int currentProgram = droneBinder.getProgram();
        for (int familyIdx = 0; familyIdx < groups.size(); familyIdx++) {
            List<NameValPair> group = groups.get(familyIdx);
            for (int groupInstIdx = 0; groupInstIdx < group.size(); groupInstIdx++) {
                if (group.get(groupInstIdx).i - 1 == currentProgram) {
                    familySpinner.setSelection(familyIdx, true);
                    setInstrumentChoices((ArrayAdapter) instrumentSpinner.getAdapter(), group);
                    instrumentSpinner.setSelection(groupInstIdx, true);
                    return;
                }
            }
        }

        throw BuildConfig.DEBUG ? new DebugException(
                String.format("Failed to set spinners to the program number %d", currentProgram)) :
                new DefaultException();

    }

    // Add a new note the GUI
    public void addNote(final int handle, final ArrayAdapter<NameValPair> pitchAdapter) {
        // Get the pitch layout
        LinearLayout layout = findViewById(R.id.pitchLayout);

        // Inflate new spinners
        LayoutInflater inflater = LayoutInflater.from(layout.getContext());
        Spinner pitchSpinner = (Spinner) inflater.inflate(R.layout.pitch_spinner, null);
        Spinner octaveSpinner = (Spinner) inflater.inflate(R.layout.octave_spinner, null);

        // Add a new note
        NoteSelector noteSelector = new NoteSelector(layout.getContext(), droneBinder, handle,
                pitchSpinner, pitchAdapter, octaveSpinner);
        noteSelectors.add(noteSelector);
        updateUI();

        // Put the new spinners in the UI layout
        final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layout.addView(noteSelector.pitchSpinner, params);
        layout.addView(noteSelector.octaveSpinner, params);
    }

    // Method to handle reading the instrument CSV file. Returns the results in these lists.
    protected List<NameValPair> readCsv(int resourceId) {
        try {
            return readCsvHelper(resourceId);
        } catch (IOException ie) {
            ie.printStackTrace();
            throw BuildConfig.DEBUG ? new DebugException("Failed to read the CSV file!") :
                    new DefaultException();
        }
    }

    // Does the work of readCsv, wrapped to catch exceptions
    protected List<NameValPair> readCsvHelper(int resourceId) throws IOException  {

        List<NameValPair> list = new ArrayList<>();

        final int itemsPerLine = 2;
        InputStream inputStream = getResources().openRawResource(resourceId);
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

        String line = bufferedReader.readLine(); // CSV header
        while ((line = bufferedReader.readLine()) != null) {
            String[] items = line.split(",");
            if (items.length != itemsPerLine) throw BuildConfig.DEBUG ? new DebugException(
                    "Invalid CSV line: " + line) : new DefaultException();
            String name = items[1].trim();
            int code = Integer.parseInt(items[0].trim());
            list.add(new NameValPair(name, code));
        }

        return list;
    }

    // Tell the service to start playing sound
    protected void play() {
        sendDisplayedBpm(bpmTextView); // In case the user never pressed "done" on the keyboard
        droneBinder.play();
    }

    // Tell the service to stop playing sound
    protected void pause() {
        droneBinder.pause();
    }

    // Retrieves the settings and updates the UI
    protected void updateUI() {
        displayBpm(droneBinder.getBpm());
        for (int i = 0; i < noteSelectors.size(); i++) {
            noteSelectors.get(i).update();
        }
    }

    // Sets the instrument to be played
    public void setInstrument(NameValPair instrument){
        droneBinder.changeProgram(instrument.i - 1);
        updateUI();
    }

    // Sends the currently-displayed BPM to the model, and updates the UI if the model changes it
    public void sendDisplayedBpm(final TextView textView) {
        // Send the new BPM to the model, query the final setting
        droneBinder.setBpm(readBpm(textView));
        updateUI(); // In case the BPM is out of bounds

        // Close the keyboard
        try {
            InputMethodManager imm = (InputMethodManager) textView.getContext()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(textView.getWindowToken(), 0);
        } catch (Exception e) {
            if (BuildConfig.DEBUG) {
                throw e; // Rethrow this for examination in debug mode
            }
        }

        /* Remove the focus from the textView. Note: something else must be focusable or else it the
         * focus will bounce back here. The easiest way is to set the containing layout as
         * focusable. */
        textView.clearFocus();
    }

    // Reads the BPM from the given textView
    public int readBpm(final TextView textView) {
        final String text = textView.getText().toString();
        return text.isEmpty() ? 0 : Integer.valueOf(text);
    }

    // Displays the current BPM
    protected void displayBpm(final int bpm) {

        bpmTextView.setText(Integer.toString(bpm));
    }

    // On restart, load an ad
    @Override
    protected void onRestart()
    {
        super.onRestart();
        interstitialAd.loadAd(new AdRequest.Builder().build());
        interstitialAd.show();
    }

    // Clean up
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(droneConnection);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // Get a string list of pitch choices
    private List<NameValPair> getPitchChoices(boolean isSharp) {

        // Extract the pitch strings
        final int resourceId = isSharp ? R.array.pitches_sharp_array : R.array.pitches_flat_array;
        final String[] pitchStrings = getResources().getStringArray(resourceId);

        // Sanity checks
        assert(pitchStrings.length <= 12);
        assert(pitchStrings[0].compareTo("A") == 0);

        // Convert to a list of (name, value) pairs
        List<NameValPair> pitchNameVals = new ArrayList<>();
        for (int i = 0; i < pitchStrings.length; i++) {
            pitchNameVals.add(new NameValPair(pitchStrings[i], i));
        }

        return pitchNameVals;
    }
}
