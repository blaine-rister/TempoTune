package com.bbrister.metrodrone;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

import android.os.Bundle;

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
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Spinner;
import android.widget.SeekBar;
import android.widget.Button;

import android.content.Context;

public class MainActivity extends DroneActivity {

    // Constants
    final private static String displaySharpsKey = "DISPLAY_SHARPS";

    // Dynamic UI items
    List<NoteSelector> noteSelectors = new ArrayList<>(); // Stores the pitches to be played

    // State
    String curSoundfontPath;
    boolean uiReady;
    boolean displaySharps;

    // Persistent UI items
    TextView bpmTextView;
    ArrayAdapter<Soundfont> familyAdapter;

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

    /**
     * Override this function to thank the user for purchasing premium mode.
     */
    @Override
    protected void onReceivePremiumMode(final boolean isPurchased, final boolean firstTime) {
        super.onReceivePremiumMode(isPurchased, firstTime);

        // Do nothing more if the activity has been re-created
        if (!firstTime) return;

        // Handle premium mode
        if (havePremium()) {
            //TODO display a toast thanking the user for supporting the app
        } else {
            // Prompt the user to upgrade to premium
            (new PremiumManager(this)).promptPremium(
                    getString(R.string.premium_prompt_full)
            );
        }
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

        // Read the list of all possible soundfonts from the CSV file
        CsvReader csvReader = new CsvReader(getResources());
        List<String[]> csvLines = csvReader.read(R.raw.sounds_list);

        // Verify the format of the CSV file
        final int pathIdx = 0;
        final int moduleIdx = 1;
        final int isFreeIdx = 2;
        final int isInstantIdx = 3;
        CsvReader.verifyHeader(csvLines, new String[] {"path", "module", "is_free", "is_instant"});

        // Verify that we have at least one soundfont
        if (csvLines.size() < 2) {
            throw BuildConfig.DEBUG_EXCEPTIONS ?
                    new DebugException("Need at least one soundfont!") : new DefaultException();
        }

        // Parse the CSV to build the list of soundfonts
        List<Soundfont> soundfonts = new ArrayList<>();
        for (int i = 1; i < csvLines.size(); i++) {
            String[] csvLine = csvLines.get(i);

            // Ignore instant soundfonts
            final boolean isInstant = str2bool(csvLine[isInstantIdx]);
            if (isInstant) {
                continue;
            }

            // Parse the "IS_FREE" entry
            final boolean isFree = str2bool(csvLine[isFreeIdx]);

            // Create the soundfont object
            soundfonts.add(new Soundfont(csvLine[pathIdx], csvLine[moduleIdx], isFree));
        }

        // Instrument name spinner
        final Spinner instrumentSpinner = findViewById(R.id.instrumentNameSpinner);
        final InstrumentIconAdapter instAdapter = new InstrumentIconAdapter(this,
                R.layout.instrument_spinner_item, R.id.instrumentName);
        instrumentSpinner.setAdapter(instAdapter);

        // Instrument family spinner
        final Spinner familySpinner = findViewById(R.id.instrumentFamilySpinner);
        final ArrayAdapter<Soundfont> familyAdapter = new ArrayAdapter<>(this,
                R.layout.instrument_name, soundfonts);
        familyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        familySpinner.setAdapter(familyAdapter);

        // Set the spinners to reflect the current program
        updateFamilySelection(familySpinner, familyAdapter);
        updateInstrumentSelection(instrumentSpinner, instAdapter);

        // Install the instrument spinner listener
        instrumentSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
                // Change the instrument program
                setInstrument((NameValPair<Integer>) adapterView.getItemAtPosition(pos));
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
                // Try to set this soundfont. If not installed, return to the previous
                if (!setSoundfont((Soundfont) adapterView.getSelectedItem(), instrumentSpinner)) {
                    updateFamilySelection(familySpinner, familyAdapter);
                }
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

    /**
     * Update the soundfont, prompting the user to install missing ones. Returns true if the
     * soundfont is installed, false otherwise.
     */
    public boolean setSoundfont(final Soundfont soundfont, final Spinner instrumentSpinner) {

        // Check if premium mode is required. If so, prompt premium purchase
        if (!havePremium() && !soundfont.isFree) {
            (new PremiumManager(this)).promptPremium(
                    getString(R.string.premium_prompt_download)
            );
            return false;
        }

        // Check if this is installed
        final DynamicModule module = new SoundfontModule(this, soundfont);
        switch (module.installStatus) {
            case INSTALLED:
                // Continue on to processing the soundfont
                break;
            case FAILED:
            case NOT_REQUESTED:
                // Allow the user to start installation. Do not set the new soundfont.
                module.promptInstallation(this, getSupportFragmentManager());
                return false;
            case PENDING:
                // Print a message
                //TODO make this an alert dialog
                DynamicModule.updateToast(this,
                        String.format(
                                getString(R.string.download_ongoing),
                                soundfont.displayName
                        )
                );
                return false;
        }

        // Check if this is the same soundfont as before
        if (!soundfont.path.equals(droneBinder.getSoundfont())) {

            // Create the installation listener
            module.setInstallListener(new DynamicModule.InstallListener() {
                @Override
                public void onInstallFinished(final boolean success) {
                    // Check for successful installation
                    //TODO change this to an alert--the user can use another soundfont
                    if (!success) {
                        throw BuildConfig.DEBUG_EXCEPTIONS ? new DebugException(
                                "Failed to request module " + module.displayName) :
                                new DefaultException();
                    }

                    // Installation succeeded. Begin loading the soundfont
                    loadSoundfont(soundfont, instrumentSpinner);
                }
            });

            // Begin installation. We need to request the module even it's previously installed
            module.installQuiet(this);
        }

        return true;
    }

    /**
     * Load the given soundfont in the synth and update instrument choices.
     */
    private void loadSoundfont(Soundfont soundfont, Spinner instrumentSpinner) {
        // Load the sounds in the synth
        droneBinder.loadSounds(soundfont.path);

        // Update the instruments. Don't rely on the spinner to do it, this is unreliable
        updateInstrumentChoices((InstrumentIconAdapter) instrumentSpinner.getAdapter());
        instrumentSpinner.setSelection(0, true);
        setInstrument((NameValPair<Integer>) instrumentSpinner.getSelectedItem());
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
            throw BuildConfig.DEBUG_EXCEPTIONS ? new DebugException("No valid instruments found!") :
                    new DefaultException();

        // Set the choices in the adapter
        adapter.clear();
        adapter.addAll(instruments);
    }

    // Set the family spinner to the given soundfont path
    private void updateFamilySelection(Spinner familySpinner,
                                       ArrayAdapter<Soundfont> adapter) {

        // Get the index of the current soundfont
        int sfIdx = -1;
        final String currentSoundfontPath = droneBinder.getSoundfont();
        for (int i = 0; i < adapter.getCount(); i++) {

            Soundfont sfChoice = adapter.getItem(i);
            if (sfChoice == null)
                continue;

            // Check for the index of the current soundfont
            if (sfChoice.path.equalsIgnoreCase(currentSoundfontPath)) {
                sfIdx = i;
                break;
            }
        }

        // Check if we found anything
        if (sfIdx < 0) {
            throw BuildConfig.DEBUG_EXCEPTIONS ? new DebugException("Failed to find soundFont " +
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
            if (adapter.getItem(i).val == currentProgram) {
                instrumentSpinner.setSelection(i, true);
                return;
            }
        }

        // Check if we failed to find the program
        throw BuildConfig.DEBUG_EXCEPTIONS ? new DebugException(
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
            if (BuildConfig.DEBUG_EXCEPTIONS) {
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

        // Convert to a list of (name, value) pairs
        List<NameValPair> pitchNameVals = new ArrayList<>();
        for (int i = 0; i < pitchStrings.length; i++) {
            pitchNameVals.add(new NameValPair(pitchStrings[i], i));
        }

        return pitchNameVals;
    }

    /**
     * Parse "yes" and "no" in CSV files
     */
    private boolean str2bool(final String str) {
        if (str.equalsIgnoreCase("yes")) {
            return true;
        } else if (str.equalsIgnoreCase("no")) {
            return false;
        } else {
            throw BuildConfig.DEBUG_EXCEPTIONS ? new DebugException(String.format(
                    "Unable to convert to bool: %s", str)) :
                    new DefaultException();
        }
    }
}
