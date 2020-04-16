package com.bbrister.tempodrone;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

import android.os.Bundle;

import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Spinner;
import android.widget.SeekBar;
import android.widget.Button;

import android.content.Context;
import android.widget.Toast;

import com.bbrister.tempodrone.preferences.BooleanPreference;
import com.bbrister.tempodrone.preferences.BytePreference;
import com.bbrister.tempodrone.preferences.ReadOnlyPreference;
import com.google.android.flexbox.FlexboxLayout;

public class MainActivity extends DroneActivity {

    // Constants
    final private static String displaySharpsKey = "DISPLAY_SHARPS";
    final private static String basicSoundfontPath = "basic.sf2";
    final private static String instantSoundfontPath = "instant.sf2";
    final private static boolean displaySharpsDefault = false;

    // Read-only configuration
    private boolean instantMode;
    private String defaultSoundfontPath;

    // Dynamic UI items
    List<NoteSelector> noteSelectors = new ArrayList<>(); // Stores the pitches to be played

    // State
    private boolean uiReady;

    /**
     * Helper method to initialize a BooleanPreference obejct for DisplaySharps.
     */
    private BooleanPreference getDisplaySharpsPreference() {
        return new BooleanPreference(this, displaySharpsKey, displaySharpsDefault);
    }

    /**
     * Check if we are displaying sharps or flats.
     */
    private boolean getDisplaySharps() {
        return getDisplaySharpsPreference().read();
    }

    /**
     * Save whether to display sharps or flats.
     */
    private void setDisplaySharps(boolean displaySharps) {
        getDisplaySharpsPreference().write(displaySharps);
    }

    /**
     * Check if this is an instant app or the full install.
     */
    private boolean isInstant() {
        // Get the all the soundfonts
        List<Soundfont> soundfonts = querySoundfonts(false);

        // Search for an instant one and see if it's installed
        for (Soundfont soundfont : soundfonts) {
            if (soundfont.isInstant()) {
                return soundfont.isInstalled();
            }
        }

        // If there are no instant soundfonts, we're not in instant mode
        return false;
    }

    // Initialize the UI when the drone is connected
    @Override
    protected void onDroneConnected() {
        super.onDroneConnected();

        // Load the list of soundfonts
        final List<Soundfont> soundfonts = querySoundfonts(!instantMode);
        if (soundfonts.isEmpty()) {
            throw BuildConfig.DEBUG_EXCEPTIONS ? new DebugException("No soundsfonts found!") :
                    new RuntimeException();
        }

        // Query the last used soundfont. If there is none, use the default one
        final String previousSoundfontPath = droneBinder.haveSoundfont() ?
                droneBinder.getSoundfont() : defaultSoundfontPath;

        // Search through the soundfont modules for this soundfont
        Soundfont previousSoundfont = null;
        for (Soundfont soundfont : soundfonts) {
            if (soundfont.path.equals(previousSoundfontPath)) {
                previousSoundfont = soundfont;
                break;
            }
        }

        // In debug mode, throw an exception if we haven't found it
        if (previousSoundfont == null) {
            if (BuildConfig.DEBUG_EXCEPTIONS) {
                throw new DebugException("Failed to find the previous soundfont " +
                        previousSoundfontPath);
            }
        }

        // Otherwise default to the first available soundfont
        final Soundfont startupSoundfont = previousSoundfont == null ? soundfonts.get(0) :
                previousSoundfont;

        // Request the soundfont then load it in the drone service
        startupSoundfont.request(new DynamicModule.InstallListener() {
            @Override
            public void onInstallFinished(boolean success) {
                // Handle installation errors
                if (!success) {
                    throw BuildConfig.DEBUG_EXCEPTIONS ? new DebugException("Failed to " +
                            "request the startup soundfont") : new RuntimeException();
                }

                // Load the soundfont in the MIDI
                droneBinder.loadSounds(startupSoundfont.path);

                // Try to change to the last-used program number. Otherwise do nothing
                final byte previousProgram = new BytePreference(getApplicationContext(),
                        DroneService.programKey, DroneService.defaultProgram).read();
                if (previousProgram >= 0) {
                    try {
                        droneBinder.changeProgram(previousProgram);
                    } catch (RuntimeException e) {
                        // In debug mode, pass this exception along
                        if (BuildConfig.DEBUG_EXCEPTIONS) {
                            throw e;
                        }

                        // In production mode, do nothing
                    }
                }

                // Populate UI elements using information from the drone service
                setupUI(soundfonts);
            }
        });
    }

    // Update the UI when drone parameters are changed by the parent class
    @Override
    protected void onDroneChanged() {
        super.onDroneChanged();
        updateUI();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check if this is in instant mode. Configure the default soundfont accordingly
        instantMode = isInstant();
        defaultSoundfontPath = instantMode ? instantSoundfontPath : basicSoundfontPath;

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

        // Prompt the user to upgrade to premium
        if (!isPurchased && firstTime) {
            (new PremiumManager(this)).promptPremium(
                    getString(R.string.premium_prompt_full)
            );
        }
    }

    /**
     * Read the list of soundfonts from the CSV file and query their installation statuses.
     * Ignores instants if 'ignoreInstant' is true.
     */
    private List<Soundfont> querySoundfonts(final boolean ignoreInstant) {
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

            // Ignore soundfonts based on instant-mode
            final boolean isInstant = str2bool(csvLine[isInstantIdx]);
            if (ignoreInstant && isInstant) {
                continue;
            }

            // Parse the "IS_FREE" entry
            final boolean isFree = str2bool(csvLine[isFreeIdx]);

            // Create the soundfont object
            soundfonts.add(new Soundfont(this, csvLine[pathIdx], csvLine[moduleIdx],
                    isFree, isInstant));
        }

        return soundfonts;
    }

    // Set the behavior of the UI components. Called after the service is bound.
    protected void setupUI(final List<Soundfont> soundfonts) {

        // BPM text
        final EditText editBpm = findViewById(R.id.editBpm);
        receiveBpm(editBpm);
        editBpm.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                switch (id) {
                    case EditorInfo.IME_ACTION_DONE:
                        // Read the text and update BPM, closing the keyboard
                        sendDisplayedBpm(textView);
                        updateUI(); // In case an invalid value was entered

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
        final ImageButton bpmIncreaseButton = findViewById(R.id.bpmIncreaseButton);
        bpmIncreaseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                droneBinder.setBpm(droneBinder.getBpm() + 1);
            }
        });

        // BPM decrease button
        final ImageButton bpmDecreaseButton = findViewById(R.id.bpmDecreaseButton);
        bpmDecreaseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                droneBinder.setBpm(droneBinder.getBpm() - 1);
            }
        });

        // Add the existing notes to the UI
        final List<Integer> handles = droneBinder.getNoteHandles();
        for (Iterator<Integer> it = handles.iterator(); it.hasNext(); ) {
            addNote(it.next());
        }

        // Add note button
        final Button addNoteButton = findViewById(R.id.addPitchButton);
        addNoteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                // Check if we have reached the note limit
                if (droneBinder.notesFull()) {
                    Toast.makeText(view.getContext(), R.string.max_notes, Toast.LENGTH_SHORT)
                            .show();
                    return;
                }

                addNote(droneBinder.addNote());
            }
        });

        // Remove pitch button
        final Button removeNoteButton = findViewById(R.id.removePitchButton);
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
                noteSelectors.remove(removeIdx);
                noteToRemove.destroy(); // Must call AFTER removing from noteSelectors

                // Remove the note from the UI layout
                FlexboxLayout layout = findViewById(R.id.pitchLayout);
                layout.removeView(noteToRemove.layout);
            }
        });

        // Toggle sharps/flats switch
        final Switch sharpSwitch = findViewById(R.id.sharpSwitch);
        sharpSwitch.setChecked(getDisplaySharps());
        sharpSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                setDisplaySharps(isChecked);
                for (NoteSelector noteSelector : noteSelectors) {
                    noteSelector.update();
                }
            }
        });

        // Velocity bar
        final SeekBar velocitySeekBar = findViewById(R.id.velocitySeekBar);
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
        final SeekBar durationSeekBar = findViewById(R.id.durationSeekBar);
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

        // Cannot change sounds in instant mode
        if (instantMode) {
            AlertDialogFragment.showDialog(getSupportFragmentManager(),
                    getString(R.string.instant_change_sounds));
            return false;
        }

        // Check if premium mode is required. If so, prompt premium purchase
        if (!havePremium() && !soundfont.isFree) {
            (new PremiumManager(this)).promptPremium(
                    getString(R.string.premium_prompt_download)
            );
            return false;
        }

        // Check if this is installed
        if (!soundfont.isInstalled()) {
                // Allow the user to start installation. Do not set the new soundfont.
                soundfont.promptInstallation();
                return false;
        }

        loadSoundfont(soundfont, instrumentSpinner);

        return true;
    }

    /**
     * Request installation of the soundfont. Load it when installation finishes.
     */
    private void loadSoundfont(final Soundfont soundfont, final Spinner instrumentSpinner) {

        // Check if this is the same soundfont as before
        if (soundfont.path.equals(droneBinder.getSoundfont()))
            return;

        // Create the installation listener and install
        soundfont.request(new DynamicModule.InstallListener() {
            @Override
            public void onInstallFinished(final boolean success) {
                // Check for successful installation
                if (!success) {
                    if (BuildConfig.DEBUG_EXCEPTIONS) {
                        throw new DebugException("Failed to request module " +
                                soundfont.displayName);
                    } else {
                        return; // Hope that installation provides an error message
                    }
                }

                // Installation succeeded. Begin loading the soundfont
                finishLoadSoundfont(soundfont, instrumentSpinner);
            }
        });
    }

    /**
     * Load the given soundfont in the synth and update instrument choices.
     */
    private void finishLoadSoundfont(Soundfont soundfont, Spinner instrumentSpinner) {
        // Load the sounds in the synth
        droneBinder.loadSounds(soundfont.path);

        // Update the instruments. Don't rely on the spinner to do it, this is unreliable
        updateInstrumentSelection(instrumentSpinner,
                (InstrumentIconAdapter) instrumentSpinner.getAdapter());
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

        // Record the current program before we change the spinner
        final int currentProgram = droneBinder.getProgram();

        // Update the spinner choices. This might trigger a program change
        updateInstrumentChoices(adapter);

        // Look for the current program in the instruments. If found, set the spinner to this
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
    public void addNote(final int handle) {
        // Get the pitch layout
        FlexboxLayout layout = findViewById(R.id.pitchLayout);

        // Add a new note
        NoteSelector noteSelector = new NoteSelector(layout.getContext(), droneBinder, handle,
                new ReadOnlyPreference<Boolean>(getDisplaySharpsPreference()));
        noteSelectors.add(noteSelector);

        // Put the new spinners in the UI layout
        final FlexboxLayout.LayoutParams params = new FlexboxLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layout.addView(noteSelector.layout);
    }

    // Toggle play/pause state
    protected void playPause() {
        sendDisplayedBpm(); // In case the user never pressed "done" on the keyboard
        super.playPause();
    }

    // Receives the BPM from the service and updates the UI
    protected void receiveBpm() {
        receiveBpm((TextView) findViewById(R.id.editBpm));
    }

    // Like the latter, avoids findViewById
    protected  void receiveBpm(final TextView textView) {
        textView.setText(Integer.toString(droneBinder.getBpm()));
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
    }

    // Like the latter, but calls findViewById
    public void sendDisplayedBpm() {
        sendDisplayedBpm((TextView) findViewById(R.id.editBpm));
    }

    // Sends the currently-displayed BPM to the model, and updates the UI if the model changes it
    public void sendDisplayedBpm(TextView textView) {

        // Send the new BPM to the model, query the final setting
        droneBinder.setBpm(readBpm(textView));

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

    // Update the UI in case another activity has changed the drone service
    @Override
    protected void onResume() {
        super.onResume();
        if (uiReady) updateUI();
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
