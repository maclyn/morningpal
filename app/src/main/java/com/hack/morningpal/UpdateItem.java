package com.hack.morningpal;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.Pair;

import java.util.ArrayList;
import java.util.List;

public class UpdateItem {
    String title;
    String description;
    String phrase;
    Drawable icon;
    String backgroundUrl;
    List<Pair<String, String>> moreInfo = new ArrayList<>();

    public UpdateItem(Context context, Intent i) throws Exception {
        String action = i.getAction();
        if(!action.equals(Constants.FOUND_DATA_INTENT)){
            throw new Exception("Invalid intent type");
        }

        String senderPackage = i.getStringExtra("sender_package");
        if(senderPackage == null){
            throw new Exception("No sender package found");
        }

        title = i.getStringExtra("title");
        if(title == null){
            throw new Exception("No title found");
        }

        description = i.getStringExtra("description");
        if(description == null){
            throw new Exception("No description found");
        }

        phrase = i.getStringExtra("spoken_phrase");
        if(phrase == null){
            throw new Exception("No phrase found");
        }

        String icon_name = i.getStringExtra("icon_name");
        icon = grabDrawable(context, senderPackage, icon_name);
        if(icon == null){
            throw new Exception("Error grabbing icon");
        }

        backgroundUrl = i.getStringExtra("background_name");

        //Find more info states
        String more_title[] = i.getStringArrayExtra("more_title");
        String more_description[] = i.getStringArrayExtra("more_description");
        if(more_title != null && more_description != null
                && more_title.length == more_description.length){
            for(int j = 0; j < more_title.length; j++){
                moreInfo.add(new Pair<>(more_title[j], more_description[j]));
            }


            if(moreInfo.size() > 4) {
                moreInfo = moreInfo.subList(0, 3);
            }

            for(int j = 0; j < more_title.length; j++) {
                Log.d("Wakeup: ", more_title[j] + " " + more_description[j]);
            }
        }
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getPhrase(){
        return phrase;
    }

    public Drawable getIcon() {
        return icon;
    }

    public String getBackgroundUrl(){
        return backgroundUrl;
    }

    public List<Pair<String, String>> getMoreInfo(){
        return moreInfo;
    }

    private Drawable grabDrawable(Context context, String senderPackage, String name) throws Exception{
        if(name == null){
            return null;
        } else {
            try {
                int resource =
                        context.getPackageManager()
                                .getResourcesForApplication(senderPackage)
                                .getIdentifier(name, "drawable", senderPackage);
                if(resource == 0){
                    return null;
                } else {
                    icon = context.getPackageManager().getResourcesForApplication(senderPackage)
                            .getDrawable(resource);
                    return icon;
                }
            } catch (Exception e) {
                return null;
            }
        }
    }
}