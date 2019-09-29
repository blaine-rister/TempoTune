package com.bbrister.metrodrone;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Class to display an instrument choice along with an icon.
 */
public class InstrumentIconAdapter extends ArrayAdapter<NameValPair<Integer>> {

    private InstrumentIcon instrumentIcon;

    public InstrumentIconAdapter(Context context, int resource, int textViewResourceId) {
        super(context, resource, textViewResourceId);

        // Initialize the icon loader
        instrumentIcon = new InstrumentIcon(context);
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return getIconView(position, convertView, parent);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return getIconView(position, convertView, parent);
    }

    public View getIconView(int position, View convertView, ViewGroup parent) {

        // Get the item to display
        NameValPair<Integer> item = getItem(position);

        // Inflate a new view if we're not provided a cached one
        if (convertView == null) {
            LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
            convertView = layoutInflater.inflate(
                    R.layout.instrument_spinner_item, parent, false);
        }

        // Update the text
        TextView label = convertView.findViewById(R.id.instrumentName);
        label.setText(item.name);

        // Update the icon
        ImageView icon = convertView.findViewById(R.id.instrumentIcon);
        icon.setImageResource(instrumentIcon.lookupDrawable(item.name));

        return convertView;
    }
}
