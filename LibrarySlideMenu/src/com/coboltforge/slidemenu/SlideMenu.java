/*
 * A sliding menu for Android, very much like the Google+ and Facebook apps have.
 *
 * Copyright (C) 2012 CoboltForge
 *
 * Based upon the great work done by stackoverflow user Scirocco (http://stackoverflow.com/a/11367825/361413), thanks a lot!
 * The XML parsing code comes from https://github.com/darvds/RibbonMenu, thanks!
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.coboltforge.slidemenu;

import java.lang.reflect.Method;
import java.util.ArrayList;

import org.xmlpull.v1.XmlPullParser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.XmlResourceParser;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.Interpolator;
import android.view.animation.TranslateAnimation;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

public class SlideMenu extends LinearLayout{

	// keys for saving/restoring instance state
	private final static String KEY_MENUSHOWN = "menuWasShown";
	private final static String KEY_STATUSBARHEIGHT = "statusBarHeight";
	private final static String KEY_SUPERSTATE = "superState";

	public static class SlideMenuItem {
		public int id;
		public Drawable icon;
		public String label;
	}

	// a simple adapter
	private static class SlideMenuAdapter extends ArrayAdapter<SlideMenuItem> {
		Activity act;
		SlideMenuItem[] items;
		Typeface itemFont;
		int selectedPosition;

		class MenuItemHolder {
			public TextView label;
			public ImageView icon;
		}

		public SlideMenuAdapter(Activity act, SlideMenuItem[] items,
				Typeface itemFont, int selectedPosition) {
			super(act, R.id.menu_label, items);
			this.act = act;
			this.items = items;
			this.itemFont = itemFont;
			this.selectedPosition = selectedPosition;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View rowView = convertView;
			if (rowView == null) {
				LayoutInflater inflater = act.getLayoutInflater();
				rowView = inflater.inflate(R.layout.slidemenu_listitem, null);
				if (position == selectedPosition) {
					rowView.setBackgroundDrawable(act.getResources()
							.getDrawable(R.drawable.selected_item_bg));
				}
				MenuItemHolder viewHolder = new MenuItemHolder();
				viewHolder.label = (TextView) rowView
						.findViewById(R.id.menu_label);
				if (itemFont != null)
					viewHolder.label.setTypeface(itemFont);
				viewHolder.icon = (ImageView) rowView
						.findViewById(R.id.menu_icon);
				rowView.setTag(viewHolder);
			}

			MenuItemHolder holder = (MenuItemHolder) rowView.getTag();
			String s = items[position].label;
			holder.label.setText(s);
			holder.icon.setImageDrawable(items[position].icon);

			return rowView;
		}
	}

	// this tells whether the menu is currently shown
	private boolean menuIsShown = false;
	// this just tells whether the menu was ever shown
	private boolean menuWasShown = false;
	private int statusHeight = -1;
	private static View menu;
	private static ViewGroup content;
	private static FrameLayout parent;
	private static int selectedPosition;
	private static int screenWidth;
	private static int menuSize;
	private Activity act;
	private Typeface font;
	private TranslateAnimation slideRightAnim;
	private TranslateAnimation slideMenuLeftAnim;
	private TranslateAnimation slideContentLeftAnim;
	private float downXValue, downYValue;

	private ArrayList<SlideMenuItem> menuItemList;
	private SlideMenuInterface.OnSlideMenuItemClickListener callback;

	/**
	 * Constructor used by the inflation apparatus. To be able to use the
	 * SlideMenu, call the {@link #init init()} method.
	 * 
	 * @param context
	 */
	public SlideMenu(Context context) {
		super(context);
	}

	/**
	 * Constructor used by the inflation apparatus. To be able to use the
	 * SlideMenu, call the {@link #init init()} method.
	 * 
	 * @param attrs
	 */
	public SlideMenu(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	/**
	 * Constructs a SlideMenu with the given menu XML.
	 * 
	 * @param act
	 *            The calling activity.
	 * @param menuResource
	 *            Menu resource identifier.
	 * @param cb
	 *            Callback to be invoked on menu item click.
	 * @param slideDuration
	 *            Slide in/out duration in milliseconds.
	 */
	public SlideMenu(Activity act, int menuResource,
			SlideMenuInterface.OnSlideMenuItemClickListener cb,
			int slideDuration, int selectedPosition) {
		super(act);
		init(act, menuResource, cb, slideDuration, selectedPosition);
	}

	/**
	 * Constructs an empty SlideMenu.
	 * 
	 * @param act
	 *            The calling activity.
	 * @param cb
	 *            Callback to be invoked on menu item click.
	 * @param slideDuration
	 *            Slide in/out duration in milliseconds.
	 */
	public SlideMenu(Activity act,
			SlideMenuInterface.OnSlideMenuItemClickListener cb,
			int slideDuration, int selectedPosition) {
		this(act, 0, cb, slideDuration, selectedPosition);
	}

	/**
	 * If inflated from XML, initializes the SlideMenu.
	 * 
	 * @param act
	 *            The calling activity.
	 * @param menuResource
	 *            Menu resource identifier, can be 0 for an empty SlideMenu.
	 * @param cb
	 *            Callback to be invoked on menu item click.
	 * @param slideDuration
	 *            Slide in/out duration in milliseconds.
	 */
	public void init(Activity act, int menuResource,
			SlideMenuInterface.OnSlideMenuItemClickListener cb,
			int slideDuration, int selectedPosition) {

		this.act = act;
		this.callback = cb;
		this.selectedPosition = selectedPosition;

		// get screen width
		DisplayMetrics displaymetrics = new DisplayMetrics();
		((Activity) getContext()).getWindowManager().getDefaultDisplay()
				.getMetrics(displaymetrics);

		// set size
		menuSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
				180, act.getResources().getDisplayMetrics());
		screenWidth = displaymetrics.widthPixels;

		// create animations accordingly
		slideRightAnim = new TranslateAnimation(menuSize, 0, 0, 0);
		slideRightAnim.setFillAfter(true);
		slideMenuLeftAnim = new TranslateAnimation(0, menuSize, 0, 0);
		slideMenuLeftAnim.setFillAfter(true);
		slideContentLeftAnim = new TranslateAnimation(-menuSize, 0, 0, 0);
		slideContentLeftAnim.setFillAfter(true);
		setAnimationDuration(slideDuration);
		// and get our menu
		parseXml(menuResource);
	}

	/**
	 * Set how long slide animation should be
	 * 
	 * @see TranslateAnimation#setDuration(long)
	 * @param slideDuration
	 *            How long to set the slide animation
	 */
	public void setAnimationDuration(long slideDuration) {
		slideRightAnim.setDuration(slideDuration);
		slideMenuLeftAnim.setDuration(slideDuration);
		slideContentLeftAnim.setDuration(slideDuration);
	}

	/**
	 * Set an Interpolator for the slide animation.
	 * 
	 * @see TranslateAnimation#setInterpolator(Interpolator)
	 * @param i
	 *            The {@link Interpolator} object to set.
	 */
	public void setAnimationInterpolator(Interpolator i) {
		slideRightAnim.setInterpolator(i);
		slideMenuLeftAnim.setInterpolator(i);
		slideContentLeftAnim.setInterpolator(i);
	}

	/**
	 * Optionally sets the font for the menu items.
	 * 
	 * @param f
	 *            A font.
	 */
	public void setFont(Typeface f) {
		font = f;
	}

	/**
	 * Dynamically adds a menu item.
	 * 
	 * @param item
	 */
	public void addMenuItem(SlideMenuItem item) {
		menuItemList.add(item);
	}

	/**
	 * Empties the SlideMenu.
	 */
	public void clearMenuItems() {
		menuItemList.clear();
	}

	/**
	 * Return true if menu is visible
	 */
	public boolean isMenuVisible() {
		return menuIsShown;
	}
	
	/**
	 * Slide the menu in.
	 */
	public void show() {
		this.show(true);
	}

	/**
	 * Set the menu to shown status without displaying any slide animation.
	 */
	public void setAsShown() {
		this.show(false);
	}

	@SuppressLint("NewApi")
	private void show(boolean animate) {
		if (!menuIsShown) {
			/*
			 * We have to adopt to status bar height in most cases, but not if
			 * there is a support actionbar!
			 */
			try {
				Method getSupportActionBar = act.getClass().getMethod(
						"getSupportActionBar", (Class[]) null);
				Object sab = getSupportActionBar.invoke(act, (Object[]) null);
				sab.toString(); // check for null

				if (android.os.Build.VERSION.SDK_INT >= 11) {
					// over api level 11? add the margin
					getStatusbarHeight();
				}
			} catch (Exception es) {
				// there is no support action bar!
				getStatusbarHeight();
			}

			// modify content layout params
			try {
				content = ((LinearLayout) act
						.findViewById(android.R.id.content).getParent());
			} catch (ClassCastException e) {
				/*
				 * When there is no title bar
				 * (android:theme="@android:style/Theme.NoTitleBar"), the
				 * android.R.id.content FrameLayout is directly attached to the
				 * DecorView, without the intermediate LinearLayout that holds
				 * the titlebar plus content.
				 */
				content = (FrameLayout) act.findViewById(android.R.id.content);
			}
			FrameLayout.LayoutParams parm = new FrameLayout.LayoutParams(-1,
					-1, 3);
			parm.setMargins(-menuSize, 0, menuSize, 0);
			content.setLayoutParams(parm);

			// animation for smooth slide-out
			if (animate)
				content.startAnimation(slideRightAnim);

			// quirk for sony xperia devices, shouldn't hurt on others
			if (Build.VERSION.SDK_INT >= 11
					&& Build.MANUFACTURER.contains("Sony") && menuWasShown)
				content.setX(-menuSize);

			// add the slide menu to parent
			parent = (FrameLayout) content.getParent();

			try {
				parent = (FrameLayout) content.getParent();
			} catch (ClassCastException e) {
				/*
				 * Most probably a LinearLayout, at least on Galaxy S3.
				 * https://github.com/bk138/LibSlideMenu/issues/12
				 */
				LinearLayout realParent = (LinearLayout) content.getParent();
				parent = new FrameLayout(act);
				realParent.addView(parent, 0); // add FrameLayout to real parent
												// of content
				realParent.removeView(content); // remove content from real
												// parent
				parent.addView(content); // add content to FrameLayout
			}

			LayoutInflater inflater = (LayoutInflater) act
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			menu = inflater.inflate(R.layout.slidemenu, null);
			FrameLayout.LayoutParams lays = new FrameLayout.LayoutParams(-1,
					-1, 3);
			lays.setMargins(0, statusHeight, 0, 0);
			menu.setLayoutParams(lays);
			parent.addView(menu);

			// set title linear layout height
			LinearLayout linearLayoutTitle = (LinearLayout) act
					.findViewById(R.id.title_linear_layout);
			int statusHeightConverted = (int) TypedValue.applyDimension(
					TypedValue.COMPLEX_UNIT_DIP, statusHeight - 2, act
							.getResources().getDisplayMetrics());
			LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
					-1, statusHeightConverted);
			linearLayoutTitle.setLayoutParams(titleParams);

			// connect the menu's listview
			ListView list = (ListView) act.findViewById(R.id.menu_listview);
			SlideMenuItem[] items = menuItemList
					.toArray(new SlideMenuItem[menuItemList.size()]);
			SlideMenuAdapter adap = new SlideMenuAdapter(act, items, font,
					selectedPosition);
			list.setAdapter(adap);
			list.setOnItemClickListener(new OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent, View view,
						int position, long id) {

					if (callback != null)
						callback.onSlideMenuItemClick(menuItemList
								.get(position).id);

					hide();
				}
			});
			
			list.setOnTouchListener(new OnTouchListener(){
				@Override
				public boolean onTouch(View view, MotionEvent event) {
					Log.d("test", "onTouch");

					// Get the action that was done on this touch event
					switch (event.getAction()) {
					case MotionEvent.ACTION_DOWN: {
						// store the X, Y value when the user's finger was pressed down
						downXValue = event.getX();
						downYValue = event.getY();
						break;
					}

					case MotionEvent.ACTION_UP: {
						// Get the X, Y value when the user released his/her finger
						float currentX = event.getX();
						float currentY = event.getY();

						// going backwards: pushing stuff to the right (hiding menu)
						if (((downXValue - currentX) < -40)
								&& (Math.abs(downYValue - currentY) < 100) || ((downXValue - currentX) < -250)) {
							hide();
							return true;
						}
						break;
					}
					}

					return false;
				}
			});

			// slide menu in
			if (animate)
				menu.startAnimation(slideRightAnim);
			
			//enableDisableViewGroup(content, false);

			menuIsShown = true;
			menuWasShown = true;
		}
	}
	/**
	 * Slide the menu out.
	 */
	@SuppressLint("NewApi")
	public void hide() {
		if (menuIsShown) {
			menu.startAnimation(slideMenuLeftAnim);
			parent.removeView(menu);

			content.startAnimation(slideContentLeftAnim);

			FrameLayout.LayoutParams parm = (FrameLayout.LayoutParams) content
					.getLayoutParams();
			parm.setMargins(0, 0, 0, 0);
			content.setLayoutParams(parm);
			//enableDisableViewGroup(content, true);

			// quirk for sony xperia devices, shouldn't hurt on others
			if (Build.VERSION.SDK_INT >= 11
					&& Build.MANUFACTURER.contains("Sony"))
				content.setX(0);
			menuIsShown = false;
		}
	}

	private void getStatusbarHeight() {
		// Only do this if not already set.
		// Especially when called from within onCreate(), this does not return
		// the true values.
		if (statusHeight == -1) {
			Rect r = new Rect();
			Window window = act.getWindow();
			window.getDecorView().getWindowVisibleDisplayFrame(r);
			statusHeight = r.top;
			statusHeight = 50;
		}
	}

	// originally:
	// http://stackoverflow.com/questions/5418510/disable-the-touch-events-for-all-the-views
	// modified for the needs here
	private void enableDisableViewGroup(ViewGroup viewGroup, boolean enabled) {
		int childCount = viewGroup.getChildCount();
		for (int i = 0; i < childCount; i++) {
			View view = viewGroup.getChildAt(i);
			if (view.isFocusable())
				view.setEnabled(enabled);
			if (view instanceof ViewGroup) {
				enableDisableViewGroup((ViewGroup) view, enabled);
			} else if (view instanceof ListView) {
				if (view.isFocusable())
					view.setEnabled(enabled);
				ListView listView = (ListView) view;
				int listChildCount = listView.getChildCount();
				for (int j = 0; j < listChildCount; j++) {
					if (view.isFocusable())
						listView.getChildAt(j).setEnabled(false);
				}
			}
		}
	}
	

	// originally: https://github.com/darvds/RibbonMenu
	// credit where credits due!
	private void parseXml(int menu) {

		menuItemList = new ArrayList<SlideMenuItem>();

		// use 0 id to indicate no menu (as specified in JavaDoc)
		if (menu == 0)
			return;

		try {
			XmlResourceParser xpp = act.getResources().getXml(menu);

			xpp.next();
			int eventType = xpp.getEventType();

			while (eventType != XmlPullParser.END_DOCUMENT) {

				if (eventType == XmlPullParser.START_TAG) {

					String elemName = xpp.getName();

					if (elemName.equals("item")) {

						String textId = xpp.getAttributeValue(
								"http://schemas.android.com/apk/res/android",
								"title");
						String iconId = xpp.getAttributeValue(
								"http://schemas.android.com/apk/res/android",
								"icon");
						String resId = xpp.getAttributeValue(
								"http://schemas.android.com/apk/res/android",
								"id");

						SlideMenuItem item = new SlideMenuItem();
						item.id = Integer.valueOf(resId.replace("@", ""));
						if (iconId != null) {
							item.icon = act.getResources().getDrawable(
									Integer.valueOf(iconId.replace("@", "")));
						}
						item.label = resourceIdToString(textId);

						menuItemList.add(item);
					}

				}

				eventType = xpp.next();

			}

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private String resourceIdToString(String text) {
		if (!text.contains("@")) {
			return text;
		} else {
			String id = text.replace("@", "");
			return act.getResources().getString(Integer.valueOf(id));

		}
	}

	@Override
	protected void onRestoreInstanceState(Parcelable state) {

		try {

			if (state instanceof Bundle) {
				Bundle bundle = (Bundle) state;

				statusHeight = bundle.getInt(KEY_STATUSBARHEIGHT);

				if (bundle.getBoolean(KEY_MENUSHOWN))
					show(false); // show without animation

				super.onRestoreInstanceState(bundle
						.getParcelable(KEY_SUPERSTATE));

				return;
			}

			super.onRestoreInstanceState(state);

		} catch (NullPointerException e) {
			// in case the menu was not declared via XML but added from code
		}
	}

	@Override
	protected Parcelable onSaveInstanceState() {

		Bundle bundle = new Bundle();
		bundle.putParcelable(KEY_SUPERSTATE, super.onSaveInstanceState());
		bundle.putBoolean(KEY_MENUSHOWN, menuIsShown);
		bundle.putInt(KEY_STATUSBARHEIGHT, statusHeight);

		return bundle;
	}

}
