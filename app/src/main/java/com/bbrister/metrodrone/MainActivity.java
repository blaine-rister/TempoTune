package com.bbrister.metrodrone;

import java.io.IOException;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

import android.os.Bundle;

import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
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
import android.widget.SpinnerAdapter;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Spinner;
import android.widget.SeekBar;
import android.widget.Button;

import android.content.Context;

public class MainActivity extends DroneActivity {

    // Constants
    final static String logTag = "metrodrone";
    final static String displaySharpsKey = "DISPLAY_SHARPS";

    // Dynamic UI items
    List<NoteSelector> noteSelectors = new ArrayList<>(); // Stores the pitches to be played

    // State
    String curSoundfontPath;
    boolean uiReady;
    boolean displaySharps;

    // Persistent UI items
    TextView bpmTextView;

    // Initialize the UI when the drone is connected
    @Override
    protected void onDroneConnected() {
        super.onDroneConnected();
        setupUI();
    }

    // Update the UI when drone parameters are changed by the parent class
    @Override
    protected void onDroneChanged() {
        super.onDroneChanged();
        updateUI();
    }

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

        // Start drawing the layout in the meantime, but don't initialize the behavior
        uiReady = false;
        setContentLayout(R.layout.content_main);
    }


    // Set the behavior of the UI components. Called after the service is bound.
    protected void setupUI() {

        // BPM text
        EditText editBpm = findViewById(R.id.editBpm);
        bpmTextView = editBpm;
        receiveBpm(); // Requires bpmTextView is initialized
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

        // Query the available soundfonts
        String[] assetPaths;
        try {
             assetPaths = getAssets().list("");
        } catch (IOException e) {
            String errMsg = e.getMessage();
            throw BuildConfig.DEBUG ? new DebugException("Error listing assets: " + errMsg) :
                    new RuntimeException(errMsg);
        }
        if (assetPaths.length < 1)
            throw BuildConfig.DEBUG ? new DebugException("Failed to find any assets!") :
                    new DefaultException();

        // Get the display names of the soundfont files
        List<NameValPair<String>> soundfonts = new ArrayList<>();
        for (String path : assetPaths) {

            // Skip all else but .sf2 files
            String fileExt = ".sf2";
            if (!path.endsWith(fileExt))
                continue;

            // Strip the file extension
            String baseName = path.substring(0, path.length() - fileExt.length());

            // Format the name for display
            char[] displayNameChars = baseName.toCharArray();
            // TODO strip white space?
            displayNameChars[0] = Character.toTitleCase(displayNameChars[0]);
            for (int i = 1; i < displayNameChars.length; i++) {
                switch (displayNameChars[i - 1]) {
                    case '_':
                        displayNameChars[i - 1] = ' '; // Convert to spaces
                    case ' ':
                        displayNameChars[i] = Character.toTitleCase(displayNameChars[i]);
                }
            }

            // Add to a (displayName, path) pair
            soundfonts.add(new NameValPair<>(new String(displayNameChars), path));
        }

        // Instrument name spinner
        final Spinner instrumentSpinner = findViewById(R.id.instrumentNameSpinner);
        final ArrayAdapter<NameValPair<Integer>> instAdapter = new ArrayAdapter<>(this,
                R.layout.instrument_spinner_item);
        instAdapter.setDropDownViewResource(android.R.layout.simple_spinner_item);
        instrumentSpinner.setAdapter(instAdapter);

        // Instrument family spinner
        Spinner familySpinner = findViewById(R.id.instrumentFamilySpinner);
        ArrayAdapter<NameValPair<String>> familyAdapter = new ArrayAdapter<>(this,
                R.layout.instrument_spinner_item, soundfonts);
        familyAdapter.setDropDownViewResource(R.layout.instrument_spinner_item);
        familySpinner.setAdapter(familyAdapter);

        // Set the spinners to reflect the current program
        updateFamilySelection(familySpinner, familyAdapter);
        updateInstrumentSelection(instrumentSpinner, instAdapter);

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

                // Load a new soundfont
                NameValPair<String> selection = (NameValPair<String>) adapterView.getSelectedItem();
                droneBinder.loadSounds(selection.val);
                updateInstrumentChoices(instAdapter);

                // Update the instrument. Don't rely on the spinner to do it, this is unreliable
                instrumentSpinner.setSelection(0, true);
                setInstrument((NameValPair) instrumentSpinner.getSelectedItem());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                // Do nothing
            }
        });

        // Update all the UI elements, to retrieve values set by other activities
        uiReady = true;
        updateUI();
    }

    // Update the list of instrument choices
    private void updateInstrumentChoices(ArrayAdapter<NameValPair<Integer>> adapter) {

        // List the instruments in the currently loaded soundfont
        List<NameValPair<Integer>> instruments = new ArrayList<>();
        for (int programNum = 0; programNum < DroneService.programMax; programNum++) {
            if (!droneBinder.queryProgram(programNum))
                continue;

            instruments.add(new NameValPair(
                    droneBinder.getProgramName(programNum),
                    programNum
            ));
        }

        // We need to have at least one instrument
        if (instruments.isEmpty())
            throw BuildConfig.DEBUG ? new DebugException("No valid instruments found!") :
                    new DefaultException();

        // Set the choices in the adapter
        adapter.clear();
        adapter.addAll(instruments);
    }

    // Set the family spinner to the given soundfont path
    private void updateFamilySelection(Spinner familySpinner,
                                       ArrayAdapter<NameValPair<String>> adapter) {

        // Get the index of the current soundfont
        int sfIdx = -1;
        final String currentSoundfontPath = droneBinder.getSoundfont();
        for (int i = 0; i < adapter.getCount(); i++) {

            NameValPair<String> sfChoice = adapter.getItem(i);
            if (sfChoice == null)
                continue;

            // Check for the index of the current soundfont
            if (sfChoice.val.equalsIgnoreCase(currentSoundfontPath)) {
                sfIdx = i;
                break;
            }
        }

        // Check if we found anything
        if (sfIdx < 0) {
            throw BuildConfig.DEBUG ? new DebugException("Failed to find soundFont " +
                    currentSoundfontPath) : new DefaultException();
        }

        // Update the spinner
        familySpinner.setSelection(sfIdx);
    }

    // Set the instrument spinners to the current program number
    private void updateInstrumentSelection(Spinner instrumentSpinner,
                                           ArrayAdapter<NameValPair<Integer>> adapter){

        // Update the spinner choices
        updateInstrumentChoices(adapter);

        // Look for the current program in the instruments. If found, set the spinners
        final int currentProgram = droneBinder.getProgram();
        for (int i = 0; i < adapter.getCount(); i++) {
            if (adapter.getItem(i).val != currentProgram) {
                instrumentSpinner.setSelection(i, true);
                return;
            }
        }

        // Check if we failed to find the program
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

    // Tell the service to start playing sound
    protected void play() {
        sendDisplayedBpm(bpmTextView); // In case the user never pressed "done" on the keyboard
        super.play();
    }

    // Receives the BPM from the service and updates the UI
    protected void receiveBpm() {
        displayBpm(droneBinder.getBpm());
    }

    // Retrieves the settings and updates the UI
    protected void updateUI() {
        receiveBpm();
        for (int i = 0; i < noteSelectors.size(); i++) {
            noteSelectors.get(i).update();
        }
    }

    // Sets the instrument to be played
    public void setInstrument(NameValPair<Integer> instrument){
        droneBinder.changeProgram(instrument.val);
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

    // Update the UI in case another activity has changed the drone service
    @Override
    protected void onResume() {
        super.onResume();
        if (uiReady) updateUI();
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
