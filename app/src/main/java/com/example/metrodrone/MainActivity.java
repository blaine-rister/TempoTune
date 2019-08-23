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

import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
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
    final static boolean testAds = true;
    final static String logTag = "metrodrone";

    // Dynamic UI items
    List<NoteSelector> noteSelectors = new ArrayList<>(); // Stores the pitches to be played

    // State
    boolean isTapping = false;
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
            droneBinder.pushUpdates(false);
            setupUI();
            updateUI();
            droneBinder.popUpdates();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            throw new RuntimeException("Lost connection to the server.");
        }

        @Override
        public void onNullBinding(ComponentName name) {
            throw new RuntimeException("The server returned null on binding.");
        }

        @Override
        public void onBindingDied(ComponentName name) {
            throw new RuntimeException("The server binding died.");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Start the drone service and bind to it. When bound, we will set up the UI.
        Intent intent = new Intent(this, DroneService.class);
        if (startService(intent) == null) {
            throw new RuntimeException("Failed to start the service.");
        }
        if (!bindService(intent, droneConnection, Context.BIND_AUTO_CREATE)) {
            throw new RuntimeException("Binding to the server returned false.");
        }

        // Start drawing the layout in the meantime, but don't initialize the behavior
        setContentView(R.layout.activity_main);

        // Initialize ads
        MobileAds.initialize(this);

        // Get the ad unit IDs
        final boolean testAds = true;
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
                if (id == EditorInfo.IME_ACTION_DONE) {
                    // Read the text and update BPM
                    droneBinder.setBpm(readBpm(textView));

                    // Remove the focus, but enable it to regain focus after touch
                    textView.setFocusable(false);
                    textView.setFocusableInTouchMode(true);

                    // Return false to continue processing the action, close keyboard
                }

                return false;
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
        final ArrayAdapter<CharSequence> pitchAdapter = ArrayAdapter.createFromResource(this,
                R.array.pitches_array, android.R.layout.simple_spinner_item);
        pitchAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);


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

        // Load the instruments and families from the CSV files
        List<NameValPair> fullInstruments = readCsv(R.raw.instruments);
        List<NameValPair> families = readCsv(R.raw.families);

        // Check each instrument to see if it's valid. Keep only the valid ones.
        List<NameValPair> instruments = new ArrayList<>();
        for (Iterator<NameValPair> it = fullInstruments.iterator(); it.hasNext();) {
            final NameValPair instrument = it.next();
            final int instCode = instrument.i - 1;
            if (droneBinder.queryProgram(instCode)) {
                instruments.add(instrument);
            } else {
                Log.w(logTag, String.format("setupUI: skipping invalid instrument %s (code %d)",
                        instrument.s, instCode));
            }
        }

        // We need to have at least one instrument
        if (instruments.isEmpty())
            throw new RuntimeException("No valid instruments found!");

        // Sort the lists by their codes
        Collections.sort(instruments);
        Collections.sort(families);

        // Pad the families array with a 'null' bookend which is greater than all the instruments
        families.add(new NameValPair(null, instruments.get(instruments.size() - 1).i + 1));

        // Group the instruments into families
        List<List<NameValPair>> groupedInstruments = new ArrayList<>();
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

        // Set up array adapters for each group
        final List<ArrayAdapter<NameValPair>> groupedInstrumentAdapters = new ArrayList<>();
        for (int i = 0; i < groupedInstruments.size(); i++) {
            ArrayAdapter<NameValPair> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, groupedInstruments.get(i));
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_item);
            groupedInstrumentAdapters.add(adapter);
        }

        // Instrument name spinner
        final Spinner instrumentSpinner = findViewById(R.id.instrumentNameSpinner);
        instrumentSpinner.setAdapter(groupedInstrumentAdapters.get(0));
        droneBinder.ignoreNextUpdate();
        instrumentSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {

                // Change the instrument program
                final int instrument = ((NameValPair) adapterView.getItemAtPosition(pos)).i - 1;
                droneBinder.changeProgram(instrument);
                updateUI();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                // Do nothing
            }
        });

        // Instrument family spinner
        Spinner familySpinner = findViewById(R.id.instrumentFamilySpinner);
        ArrayAdapter<String> familyAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, familyNames);
        familyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_item);
        familySpinner.setAdapter(familyAdapter);
        droneBinder.ignoreNextUpdate();
        familySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
                // Change the data of the instrument name spinner
                instrumentSpinner.setAdapter(groupedInstrumentAdapters.get(pos));
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                // Do nothing
            }
        });

        // Set the spinners to reflect the current program
        updateInstrumentSpinners(groupedInstruments, familySpinner, instrumentSpinner);

        // Tempo tapper
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
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

    // Set the instrument spinners to the current program
    private void updateInstrumentSpinners(List<List<NameValPair>> groups, Spinner familySpinner,
                                       Spinner instrumentSpinner){

        // Look for the current program in the instrument groups. If found, set the spinners
        final int currentProgram = droneBinder.getProgram();
        int instrumentIdx = 0;
        for (int familyIdx = 0; familyIdx < groups.size(); familyIdx++) {
            List<NameValPair> group = groups.get(familyIdx);
            for (int groupInstIdx = 0; groupInstIdx < group.size(); groupInstIdx++) {
                if (instrumentIdx == currentProgram) {
                    droneBinder.ignoreNextUpdate();
                    familySpinner.setSelection(familyIdx);
                    droneBinder.ignoreNextUpdate();
                    instrumentSpinner.setSelection(groupInstIdx);
                    return;
                }
                instrumentIdx++;
            }
        }

        throw new RuntimeException(
                String.format("Failed to set spinners to the program number %d", currentProgram));

    }

    // Add a new note the GUI
    public void addNote(final int handle, final ArrayAdapter<CharSequence> pitchAdapter) {
        // Get the pitch layout
        LinearLayout layout = findViewById(R.id.pitchLayout);

        // Inflate new spinners
        LayoutInflater inflater = LayoutInflater.from(layout.getContext());
        Spinner pitchSpinner = (Spinner) inflater.inflate(R.layout.pitch_spinner, null);
        Spinner octaveSpinner = (Spinner) inflater.inflate(R.layout.pitch_spinner, null);

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

    // Check the BPM when 'back' is pressed: the user may use this to exit the keyboard
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        droneBinder.setBpm(readBpm(bpmTextView));
        updateUI();
    }

    // Method to handle reading the instrument CSV file. Returns the results in these lists.
    protected List<NameValPair> readCsv(int resourceId) {
        try {
            return readCsvHelper(resourceId);
        } catch (IOException ie) {
            ie.printStackTrace();
            throw new RuntimeException("Failed to read the CSV file!");
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
            if (items.length != itemsPerLine) throw new RuntimeException(
                    "Invalid CSV line: " + line);
            String name = items[1].trim();
            int code = Integer.parseInt(items[0].trim());
            list.add(new NameValPair(name, code));
        }

        return list;
    }

    // Tell the service to start playing sound
    protected void play() {
        droneBinder.play();
    }

    // Tell the service to stop playing sound
    protected void pause() {
        droneBinder.pause();
    }

    // Retrieves the settings and updates the UI
    protected void updateUI() {
        droneBinder.pushUpdates(false);
        displayBpm(droneBinder.getBpm());
        for (int i = 0; i < noteSelectors.size(); i++) {
            noteSelectors.get(i).update();
        }
        droneBinder.popUpdates();
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
}
