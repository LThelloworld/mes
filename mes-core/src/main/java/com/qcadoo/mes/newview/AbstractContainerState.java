package com.qcadoo.mes.newview;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

public abstract class AbstractContainerState extends AbstractComponentState implements ContainerState {

    private final Map<String, ComponentState> children = new HashMap<String, ComponentState>();

    @Override
    public void initialize(final JSONObject json, final Locale locale) throws JSONException {
        super.initialize(json, locale);

        JSONObject childerJson = json.getJSONObject(JSON_CHILDREN);

        for (Map.Entry<String, ComponentState> child : children.entrySet()) {
            child.getValue().initialize(childerJson.getJSONObject(child.getKey()), locale);
        }
    }

    @Override
    public void beforeRender() {
        super.beforeRender();
        for (ComponentState child : children.values()) {
            child.beforeRender();
        }
    }

    @Override
    public JSONObject render() throws JSONException {
        JSONObject json = super.render();

        JSONObject childerJson = new JSONObject();

        for (Map.Entry<String, ComponentState> child : children.entrySet()) {
            childerJson.put(child.getKey(), child.getValue().render());
        }

        json.put(JSON_CHILDREN, childerJson);

        return json;
    }

    @Override
    public Map<String, ComponentState> getChildren() {
        return children;
    }

    @Override
    public ComponentState getChild(final String name) {
        return children.get(name);
    }

    @Override
    public void addChild(final ComponentState state) {
        children.put(state.getName(), state);
    }

}
