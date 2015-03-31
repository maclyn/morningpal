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
    private static final String TAG = "UpdateItem";
    List<PageDescription> pages;
    String backgroundUrl;

    public UpdateItem(Context context, Intent i) throws Exception {
        String action = i.getAction();
        if(!action.equals(Constants.FOUND_DATA_INTENT)){
            throw new Exception("Invalid intent type");
        }

        String senderPackage = i.getStringExtra("sender_package");
        if(senderPackage == null){
            throw new Exception("No sender package found");
        }

        //Get individual bits now
        String names[] = i.getStringArrayExtra("names");
        String descs[] = i.getStringArrayExtra("descriptions");
        String phrases[] = i.getStringArrayExtra("phrases");
        String urls[] = i.getStringArrayExtra("urls");
        String icons[] = i.getStringArrayExtra("drawable_names");

        if(names == null || descs == null || phrases == null || urls == null || icons == null){
            throw new Exception("Missing required bits");
        }

        if(names.length != descs.length ||
            descs.length != phrases.length ||
            phrases.length != urls.length ||
            urls.length != icons.length || names.length == 0){
            throw new Exception("Invalid lengths");
        }

        pages = new ArrayList<>(names.length);
        for(int j = 0; j < names.length; j++){
            pages.add(new PageDescription(names[j], descs[j], phrases[j], urls[j],
                    grabDrawable(context, senderPackage, icons[j])));
        }

        backgroundUrl = i.getStringExtra("background_name");
    }

    public List<PageDescription> getPages(){
        return pages;
    }

    public String getBackgroundUrl(){
        return backgroundUrl;
    }

    private Drawable grabDrawable(Context context, String senderPackage, String name) throws Exception{
        Drawable icon;

        if(name == null){
            return context.getResources().getDrawable(android.R.drawable.sym_def_app_icon);
        } else {
            try {
                int resource =
                        context.getPackageManager()
                                .getResourcesForApplication(senderPackage)
                                .getIdentifier(name, "drawable", senderPackage);
                if(resource == 0){
                    return context.getResources().getDrawable(android.R.drawable.sym_def_app_icon);
                } else {
                    icon = context.getPackageManager().getResourcesForApplication(senderPackage)
                            .getDrawable(resource);
                    return icon;
                }
            } catch (Exception e) {
                return context.getResources().getDrawable(android.R.drawable.sym_def_app_icon);
            }
        }
    }
}