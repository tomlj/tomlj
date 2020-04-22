package org.tomlj;

import java.util.Iterator;
import java.util.LinkedHashMap;

public class JSONPath {

    private static LinkedHashMap<String, Object> map;
    
	public static void validateJSON(String json) {
        new JSONObject(json);
    }
    
	public static LinkedHashMap<String, Object> setJsonPaths(String json) {
        map = new LinkedHashMap<String, Object>();
        JSONObject object = new JSONObject(json);
        String jsonPath = "$";
        if(json != JSONObject.NULL) {
            readObject(object, jsonPath);
        }
        return map;
    }

    private static void readObject(JSONObject object, String jsonPath) {
        Iterator<String> keysItr = object.keys();
        String parentPath = jsonPath;
        while(keysItr.hasNext()) {
            String key = keysItr.next();
            Object value = object.get(key);
            jsonPath = parentPath + "." + "[" + key + "]";

            if(value instanceof JSONArray) {            
                readArray((JSONArray) value, jsonPath);
            }
            else if(value instanceof JSONObject) {
                readObject((JSONObject) value, jsonPath);
            } else {
            	map.put(jsonPath, value);    
            }          
        }  
    }

    private static void readArray(JSONArray array, String jsonPath) {      
        String parentPath = jsonPath;
        for(int i = 0; i < array.length(); i++) {
            Object value = array.get(i);        
            jsonPath = parentPath + "[" + i + "]";

            if(value instanceof JSONArray) {
                readArray((JSONArray) value, jsonPath);
            } else if(value instanceof JSONObject) {                
                readObject((JSONObject) value, jsonPath);
            } else {
            	map.put(jsonPath, value);
            }
        }
    }
}