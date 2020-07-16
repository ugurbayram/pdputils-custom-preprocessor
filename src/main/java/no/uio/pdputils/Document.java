package no.uio.pdputils;

import java.util.ArrayList;

/**
 * @author ugurb@ifi.uio.no
 */
public class Document {
    private ArrayList<String> attributes;
    private boolean context;

    @Override
    public String toString() {
        return "Document [context=" + context + ", attributes=" + attributes + "]";
    }

    public ArrayList<String> getAttributes() {
        return attributes;
    }

    public void setAttributes(ArrayList attributes) {
        this.attributes = attributes;
    }

    public boolean isContext() {
        return context;
    }

    public void setContext(boolean context) {
        this.context = context;
    }
}
