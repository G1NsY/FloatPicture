package tool.xfy9326.floatpicture.Utils;


import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import tool.xfy9326.floatpicture.Methods.CodeMethods;
import tool.xfy9326.floatpicture.Methods.IOMethods;

public class PictureData {

    private static final String DataFileName = "PictureData.list";
    private static final String ListFileName = "PictureList.list";
    private static final String OrderFileName = "PictureOrder.list";
    private String id;
    private JSONObject detailObject;
    private JSONObject listObject;
    private JSONObject dataObject;

    public PictureData() {
    }

    public void setDataControl(String id) {
        this.id = id;
        this.listObject = getJSONFile(ListFileName);
        this.dataObject = getJSONFile(DataFileName);
        this.detailObject = getDetailObject(this.id);
    }

    @SuppressWarnings("SameParameterValue")
    public void put(String name, boolean value) {
        try {
            detailObject.put(name, value);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unused")
    public void put(String name, String value) {
        try {
            detailObject.put(name, CodeMethods.unicodeEncode(value));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void put(String name, int value) {
        try {
            detailObject.put(name, value);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("SameParameterValue")
    public void put(String name, float value) {
        try {
            detailObject.put(name, value);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("SameParameterValue")
    public boolean getBoolean(String name, boolean defaultValue) {
        if (detailObject.has(name)) {
            try {
                return detailObject.getBoolean(name);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return defaultValue;
    }

    public boolean contains(String name) {
        return detailObject != null && detailObject.has(name);
    }

    @SuppressWarnings("unused")
    public String getString(String name, String defaultValue) {
        if (detailObject.has(name)) {
            try {
                return CodeMethods.unicodeDecode(detailObject.getString(name));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return defaultValue;
    }

    public int getInt(String name, int defaultValue) {
        if (detailObject.has(name)) {
            try {
                return detailObject.getInt(name);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return defaultValue;
    }

    @SuppressWarnings("SameParameterValue")
    public float getFloat(String name, float defaultValue) {
        if (detailObject.has(name)) {
            try {
                return Float.parseFloat(detailObject.get(name) + "f");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return defaultValue;
    }

    public void commit(String pictureName) {
        try {
            boolean isNewPicture = false;
            if (pictureName != null) {
                isNewPicture = !listObject.has(id);
                listObject.put(id, pictureName);
            }
            dataObject.put(id, detailObject);
            setJSONFile(ListFileName, listObject);
            setJSONFile(DataFileName, dataObject);
            if (isNewPicture) {
                ArrayList<String> order = getPictureOrder();
                order.add(id);
                savePictureOrder(order);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void remove() {
        if (listObject.has(id)) {
            listObject.remove(id);
            dataObject.remove(id);
            setJSONFile(ListFileName, listObject);
            setJSONFile(DataFileName, dataObject);
            ArrayList<String> order = getPictureOrder();
            order.remove(id);
            savePictureOrder(order);
        }
    }

    private JSONObject getDetailObject(String id) {
        if (dataObject.has(id)) {
            try {
                return dataObject.getJSONObject(id);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return new JSONObject();
    }

    public LinkedHashMap<String, String> getListArray() {
        JSONObject listObject = getJSONFile(ListFileName);
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        try {
            ArrayList<String> storedOrder = getPictureOrder();
            ArrayList<String> order = new ArrayList<>(storedOrder);
            Set<String> knownIds = new HashSet<>();

            for (String pictureId : order) {
                if (listObject.has(pictureId) && knownIds.add(pictureId)) {
                    result.put(pictureId, listObject.getString(pictureId));
                }
            }

            Iterator<String> iterator = listObject.keys();
            while (iterator.hasNext()) {
                String pictureId = iterator.next();
                if (knownIds.add(pictureId)) {
                    result.put(pictureId, listObject.getString(pictureId));
                    order.add(pictureId);
                }
            }

            if (!new ArrayList<>(result.keySet()).equals(storedOrder)) {
                savePictureOrder(new ArrayList<>(result.keySet()));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return result;
    }

    public void savePictureOrder(List<String> requestedOrder) {
        JSONObject listObject = getJSONFile(ListFileName);
        JSONArray orderArray = new JSONArray();
        Set<String> addedIds = new HashSet<>();

        for (String pictureId : requestedOrder) {
            if (listObject.has(pictureId) && addedIds.add(pictureId)) {
                orderArray.put(pictureId);
            }
        }

        Iterator<String> iterator = listObject.keys();
        while (iterator.hasNext()) {
            String pictureId = iterator.next();
            if (addedIds.add(pictureId)) {
                orderArray.put(pictureId);
            }
        }
        IOMethods.writeFile(orderArray.toString(), Config.DEFAULT_DATA_DIR + OrderFileName);
    }

    public void setExclusivePictureVisible(String visiblePictureId) {
        JSONObject listObject = getJSONFile(ListFileName);
        JSONObject dataObject = getJSONFile(DataFileName);
        Iterator<String> iterator = listObject.keys();
        while (iterator.hasNext()) {
            String pictureId = iterator.next();
            try {
                JSONObject detail = dataObject.has(pictureId)
                        ? dataObject.getJSONObject(pictureId)
                        : new JSONObject();
                detail.put(Config.DATA_PICTURE_SHOW_ENABLED, pictureId.equals(visiblePictureId));
                dataObject.put(pictureId, detail);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        setJSONFile(DataFileName, dataObject);
    }

    private ArrayList<String> getPictureOrder() {
        ArrayList<String> order = new ArrayList<>();
        String content = IOMethods.readFile(Config.DEFAULT_DATA_DIR + OrderFileName);
        if (content == null || content.isEmpty()) {
            return order;
        }
        try {
            JSONArray orderArray = new JSONArray(content);
            for (int i = 0; i < orderArray.length(); i++) {
                order.add(orderArray.getString(i));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return order;
    }

    private JSONObject getJSONFile(String FileName) {
        String content = IOMethods.readFile(Config.DEFAULT_DATA_DIR + FileName);
        if (content != null) {
            try {
                if (!content.isEmpty()) {
                    return new JSONObject(content);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return new JSONObject();
    }

    @SuppressWarnings("UnusedReturnValue")
    private boolean setJSONFile(String FileName, JSONObject jsonObject) {
        return IOMethods.writeFile(jsonObject.toString(), Config.DEFAULT_DATA_DIR + FileName);
    }

}
