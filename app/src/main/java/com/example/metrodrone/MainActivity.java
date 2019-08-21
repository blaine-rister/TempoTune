package com.example.metrodrone;

import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import android.os.Bundle;

import android.content.Intent;
import android.content.ComponentName;
import android.content.ServiceConnection;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

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
    final boolean testAds = true;

    // Sound parameters
    int bpm = 80;
    int ketLimitLo = 0;
    int keyLimitHi = 127;
    NoteSettings settings = new NoteSettings(); // Global settings varying smoothly, incl. velocity
    List<Note> notes = new ArrayList<>(); // Stores the pitches to be played

    // State
    boolean isPlaying = false;
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
            createUI();
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

        // Start the drone service and bind to it. When bound, we will create the UI.
        Intent intent = new Intent(this, DroneService.class);
        if (!bindService(intent, droneConnection, Context.BIND_AUTO_CREATE)) {
            throw new RuntimeException("Binding to the server returned false.");
        }
    }


    // Creates the UI components. Refactored to be called after the drone service is bound
    protected void createUI() {
        // Set up the toolbar
        setContentView(R.layout.activity_main);
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
                    addBpm(readBpm(textView)- bpm);

                    // Remove the focus, but enable it to regain focus after touch
                    textView.setFocusable(false);
                    textView.setFocusableInTouchMode(true);

                    // Return false to continue processing the action, close keyboard
                }

                return false;
            }
        });
        displayBpm();


        // BPM increase button
        ImageButton bpmIncreaseButton = findViewById(R.id.bpmIncreaseButton);
        bpmIncreaseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                bpm = readBpm(bpmTextView);
                addBpm(1);
            }
        });

        // BPM decrease button
        ImageButton bpmDecreaseButton = findViewById(R.id.bpmDecreaseButton);
        bpmDecreaseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                bpm = readBpm(bpmTextView);
                addBpm(-1);
            }
        });

        // Create the pitch adapter
        final ArrayAdapter<CharSequence> pitchAdapter = ArrayAdapter.createFromResource(this,
                R.array.pitches_array, android.R.layout.simple_spinner_item);
        pitchAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // Set up the note spinners which come built-in to the UI
        notes.add(new Note(
                this,
                (Spinner) findViewById(R.id.pitchSpinner),
                pitchAdapter,
                (Spinner) findViewById(R.id.octaveSpinner)
        ) {
            @Override
            public void onPitchChanged() {
                update(); // Update the drone when the pitch is changed
            }
        });

        // Add pitch button
        final Button addPitchButton = findViewById(R.id.addPitchButton);
        addPitchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                //TODO: there could be some bug in Android about this
                // See this link, maybe try this fix: https://stackoverflow.com/questions/15425151/change-relative-layout-width-and-height-dynamically
                // Or, put the first pitch spinner outside of here, set this sub-layout WITHOUT
                // wrap_content

                // Get the pitch layout
                LinearLayout layout = findViewById(R.id.pitchLayout);

                // Inflate new spinners
                LayoutInflater inflater = LayoutInflater.from(layout.getContext());
                Spinner pitchSpinner = (Spinner) inflater.inflate(R.layout.pitch_spinner, null);
                Spinner octaveSpinner = (Spinner) inflater.inflate(R.layout.pitch_spinner, null);

                // Add a new note
                Note note = new Note(layout.getContext(), pitchSpinner, pitchAdapter,
                        octaveSpinner) {
                    @Override public void onPitchChanged() {
                        update(); // Update the drone when the pitch is changed
                    }
                };
                noteSetLimits(note);
                notes.add(note);
                update();

                // Put the new spinners in the UI layout
                final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                layout.addView(note.pitchSpinner, params);
                layout.addView(note.octaveSpinner, params);
            }
        });

        // Remove pitch button
        Button removePitchButton = findViewById(R.id.removePitchButton);
        removePitchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                // Do nothing if there are no pitches left to remove
                if (notes.isEmpty())
                    return;

                // Get the last note
                final int removeIdx = notes.size() - 1;
                Note noteToRemove = notes.get(removeIdx);

                // Remove the note from the sounding pitches
                notes.remove(removeIdx);
                update();

                // Remove the note from the UI layout
                LinearLayout layout = findViewById(R.id.pitchLayout);
                layout.removeView(noteToRemove.octaveSpinner);
                layout.removeView(noteToRemove.pitchSpinner);
            }
        });

        // Velocity bar
        SeekBar velocitySeekBar = findViewById(R.id.velocitySeekBar);
        velocitySeekBar.setProgress((int) Math.floor(settings.velocity * velocitySeekBar.getMax()));
        velocitySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                settings.velocity = (double) progress / seekBar.getMax(); // Assumes min is 0, to
                    // check this requires higher API level
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Do nothing
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Update the drone with whatever changes were made
                update();
            }
        });

        // Duration bar
        SeekBar durationSeekBar = findViewById(R.id.durationSeekBar);
        durationSeekBar.setProgress((int) Math.floor(settings.duration * durationSeekBar.getMax()));
        durationSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                settings.duration = (double) progress / seekBar.getMax(); // Assumes min is 0, to
                // check this requires higher API level
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Do nothing
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Update the drone with whatever changes were made
                update();
            }
        });

        // Load the instruments and families from the CSV files
        List<NameValPair> instruments = readCsv(R.raw.instruments);
        List<NameValPair> families = readCsv(R.raw.families);

        // Sort the lists by their codes
        Collections.sort(instruments);
        Collections.sort(families);

        // Group the instruments into families
        List<List<NameValPair>> groupedInstruments = new ArrayList<>();
        for (int i = 0; i < instruments.size(); i++) {
            // Get the current item
            final NameValPair instrument = instruments.get(i);

            // Check if we need to bump up to the next family
            final int currentNumGroups = groupedInstruments.size();
            if (currentNumGroups < families.size()) {
                final NameValPair nextFamily = families.get(currentNumGroups);
                if (instrument.i >= nextFamily.i) {
                    groupedInstruments.add(new ArrayList<NameValPair>());
                }
            }

            // Add this item to the current sub-list, which is the most recent one
            groupedInstruments.get(groupedInstruments.size() - 1).add(instrument);
        }

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
        instrumentSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {

                // Change the instrument program
                final int instrument = ((NameValPair) adapterView.getItemAtPosition(pos)).i - 1;
                droneBinder.changeProgram(instrument);

                // Get the key limits
                ketLimitLo = droneBinder.getKeyMin();
                keyLimitHi = droneBinder.getKeyMax();

                // Send the new limits to the note selectors
                for (int i = 0; i < notes.size(); i++) {
                    noteSetLimits(notes.get(i));
                }

                // Update the sound
                update();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                // Do nothing
            }
        });

        // Extract the family names
        List<String> familyNames = new ArrayList<>();
        for (int i = 0; i < families.size(); i++) {
            familyNames.add(families.get(i).s);
        }

        // Instrument family spinner
        Spinner familySpinner = findViewById(R.id.instrumentFamilySpinner);
        ArrayAdapter<String> familyAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, familyNames);
        familyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_item);
        familySpinner.setAdapter(familyAdapter);
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
                            setBpm(bpm);
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

    // Check the BPM when 'back' is pressed: the user may use this to exit the keyboard
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        setBpm(readBpm(bpmTextView));
        update();
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

    // Set the pitch limits of a Note object
    protected void noteSetLimits(Note note) {
        note.setLimits(ketLimitLo, keyLimitHi);
    }

    // Tell the service to start playing sound
    protected void play() {
        droneBinder.play(bpm, notes, settings.getReader());
        isPlaying = true;
    }

    // Tell the service to stop playing sound
    protected void pause() {
        droneBinder.pause();
        isPlaying = false;
    }

    // Updates the UI, and if playing, updates the drone
    protected void update() {
        displayBpm();
        if (isPlaying) play();
    }


    // Reads the BPM from the given textView
    public int readBpm(final TextView textView) {
        final String text = textView.getText().toString();
        return text.isEmpty() ? 0 : Integer.valueOf(text);
    }

    // Add the specified amount to the BPM. Updates the BPM view as well.
    protected void addBpm(int increase) {
        setBpm(bpm + increase);
    }

    // Set the BPM, while clamping to the allowable range
    protected void setBpm(int bpm) {
        final int maxBpm = 512; // Maximum allowed BPM
        this.bpm = Math.min(maxBpm, Math.max(0, bpm));
        update();
    }

    // Displays the current BPM
    protected void displayBpm() {
        bpmTextView.setText(Integer.toString((int) bpm));
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
