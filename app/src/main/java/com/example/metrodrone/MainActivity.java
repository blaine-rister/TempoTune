package com.example.metrodrone;

import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import android.app.AlertDialog;
import android.os.Bundle;

import android.content.Intent;
import android.content.ComponentName;
import android.content.ServiceConnection;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.view.KeyEvent;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;

import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Spinner;
import android.widget.SeekBar;

import android.content.Context;

import android.os.IBinder;
import com.example.metrodrone.DroneService.DroneBinder;

public class MainActivity extends AppCompatActivity {

    // Sound parameters
    int bpm = 80;
    int pitch = 0; // 0-11
    int octave = 3; // 0-7
    int instrument = 0; // Piano
    NoteSettings settings = new NoteSettings(); // Global settings varying smoothly, incl. velocity

    // State
    boolean isPlaying = false;
    boolean isTapping = false;
    TempoTapper tempoTapper;

    // Persistent UI items
    TextView bpmTextView;

    // Service interface
    DroneBinder droneBinder;
    boolean isBound = false;
    private ServiceConnection droneConnection = new ServiceConnection()
    {
        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            droneBinder = (DroneBinder) service;
            isBound = true;
            onResume(); // Start the app
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            fatalErrorDialog("Lost connection to the server.");
        }

        @Override
        public void onNullBinding(ComponentName name) {
            fatalErrorDialog("The server returned null on binding.");
        }

        @Override
        public void onBindingDied(ComponentName name) {
            fatalErrorDialog("The server binding died.");
        }
    };

    // Displays an error message and quits
    public void fatalErrorDialog(String message) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setCancelable(true);
        alertDialogBuilder.setMessage(message + "\nThis app will close.");
        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show(); // Shows the message
        super.finish(); // Shuts down the app
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

        // Pitch spinner
        Spinner pitchSpinner = findViewById(R.id.pitchSpinner);
        ArrayAdapter<CharSequence> pitchAdapter = ArrayAdapter.createFromResource(this,
                R.array.pitches_array, android.R.layout.simple_spinner_item);
        pitchAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        pitchSpinner.setAdapter(pitchAdapter);
        pitchSpinner.setSelection(pitch);
        pitchSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
                setPitch((String) adapterView.getItemAtPosition(pos));
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                // Do nothing
            }
        });

        // Octave spinner
        Spinner octaveSpinner = findViewById(R.id.octaveSpinner);
        final Integer[] octaves =  {0, 1, 2, 3, 4, 5, 6, 7};
        ArrayAdapter<Integer> octaveAdapter = new ArrayAdapter<Integer>(this,
                android.R.layout.simple_spinner_item, octaves);
        octaveAdapter.setDropDownViewResource(android.R.layout.simple_spinner_item);
        octaveSpinner.setAdapter(octaveAdapter);
        octaveSpinner.setSelection(octave);
        octaveSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
                octave = (Integer) adapterView.getItemAtPosition(pos);
                update();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                // Do nothing
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
                // Do nothing
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
                // Do nothing
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
                instrument = ((NameValPair) adapterView.getItemAtPosition(pos)).i - 1;
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

        // Start the drone service and bind to it
        Intent intent = new Intent(this, DroneService.class);
        if (!bindService(intent, droneConnection, Context.BIND_AUTO_CREATE)) {
            fatalErrorDialog("Binding to the server returned false.");
        }
    }

    // Method to handle reading the instrument CSV file. Returns the results in these lists.
    protected List<NameValPair> readCsv(int resourceId) {
        try {
            return readCsvHelper(resourceId);
        } catch (IOException ie) {
            ie.printStackTrace();
            fatalErrorDialog("Failed to read the CSV file!");
            return null;
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
            if (items.length != itemsPerLine) fatalErrorDialog("Invalid CSV line: " + line);
            String name = items[1].trim();
            int code = Integer.parseInt(items[0].trim());
            list.add(new NameValPair(name, code));
        }

        return list;
    }

    // Tell the service to start playing sound
    protected void play() {
        if (!isBound) return;

        droneBinder.play(bpm, pitch, octave, instrument, settings.getReader());
        isPlaying = true;
    }

    // Tell the service to stop playing sound
    protected void pause() {
        if (!isBound) return;

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

    // Updates the pitch given the pitch name in pitchStr
    protected void setPitch(CharSequence pitchStr) {
        // Convert the first character to an ASCII code
        final char firstChar = pitchStr.charAt(0);
        final boolean isSharp = pitchStr.length() > 1;
        final int firstCharAscii = (int) firstChar;

        // Make sure the character is in the range A-
        final int asciiA = (int) 'A';
        final int asciiG =  (int) 'G';
        if (firstCharAscii < asciiA || firstCharAscii > asciiG) fatalErrorDialog("Invalid pitch " +
                pitchStr);

        // Convert the natural pitch using a lookup table
        final int[] pitchLookup = {
                0,  // A, A#
                2,  // B
                3,  // C, C#
                5,  // D, D#
                7,  // E
                8,  // F, F#
                10,  // G, G#
        };
        final int naturalPitch = pitchLookup[firstCharAscii - asciiA];

        // Add sharps, update the drone
        pitch = isSharp ? naturalPitch + 1 : naturalPitch;
        update();
    }

    // On resume
    @Override
    protected void onResume()
    {
        super.onResume();
    }

    // On pause
    @Override
    protected void onPause()
    {
        super.onPause();
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
