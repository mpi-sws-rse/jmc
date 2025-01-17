package org.mpisws.strategies.trust;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Represents a location object used by the events of the algorithm. */
public record Location(Object sharedObject) {
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Location location = (Location) obj;
        return sharedObject.equals(location.sharedObject);
    }

    @Override
    public int hashCode() {
        return sharedObject.hashCode();
    }

    @Override
    public String toString() {
        return Integer.toString(sharedObject.hashCode());
    }

    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("sharedObject", sharedObject.toString());
        json.addProperty("hashCode", sharedObject.hashCode());
        return json;
    }
}
