/*
SoftVolume.java
Copyright (C) 2011  Belledonne Communications, Grenoble, France

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/
package org.linphone;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.linphone.assistant.AssistantActivity;
import org.linphone.core.DialPlan;
import org.linphone.core.LinphoneAccountCreator;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneChatMessage;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.mediastream.Log;
import org.linphone.mediastream.video.capture.hwconf.Hacks;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.telephony.TelephonyManager;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Helpers.
 * @author Guillaume Beraudo
 *
 */
public final class LinphoneUtils {
	private static Context context = null;

	private LinphoneUtils(){}

	//private static final String sipAddressRegExp = "^(sip:)?(\\+)?[a-z0-9]+([_\\.-][a-z0-9]+)*@([a-z0-9]+([\\.-][a-z0-9]+)*)+\\.[a-z]{2,}(:[0-9]{2,5})?$";
	//private static final String strictSipAddressRegExp = "^sip:(\\+)?[a-z0-9]+([_\\.-][a-z0-9]+)*@([a-z0-9]+([\\.-][a-z0-9]+)*)+\\.[a-z]{2,}$";

	public static boolean isSipAddress(String numberOrAddress) {
		try {
			LinphoneCoreFactory.instance().createLinphoneAddress(numberOrAddress);
			return true;
		} catch (LinphoneCoreException e) {
			return false;
		}
	}

	public static boolean isNumberAddress(String numberOrAddress) {
		LinphoneProxyConfig proxy = LinphoneManager.getLc().createProxyConfig();
		return proxy.normalizePhoneNumber(numberOrAddress) != null;
	}

	public static boolean isStrictSipAddress(String numberOrAddress) {
		return isSipAddress(numberOrAddress) && numberOrAddress.startsWith("sip:");
	}

	public static String getAddressDisplayName(String uri){
		LinphoneAddress lAddress;
		try {
			lAddress = LinphoneCoreFactory.instance().createLinphoneAddress(uri);
			return getAddressDisplayName(lAddress);
		} catch (LinphoneCoreException e) {
			return null;
		}
	}

	public static String getAddressDisplayName(LinphoneAddress address){
		if(address.getDisplayName() != null) {
			return address.getDisplayName();
		} else {
			if(address.getUserName() != null){
				return address.getUserName();
			} else {
				return address.asStringUriOnly();
			}
		}
	}

	public static String getUsernameFromAddress(String address) {
		if (address.contains("sip:"))
			address = address.replace("sip:", "");

		if (address.contains("@"))
			address = address.split("@")[0];

		return address;
	}

	public static boolean onKeyBackGoHome(Activity activity, int keyCode, KeyEvent event) {
		if (!(keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0)) {
			return false; // continue
		}

		activity.startActivity(new Intent()
			.setAction(Intent.ACTION_MAIN)
			.addCategory(Intent.CATEGORY_HOME));
		return true;
	}

	public static String timestampToHumanDate(Context context, long timestamp, String format) {
		try {
			Calendar cal = Calendar.getInstance();
			cal.setTimeInMillis(timestamp);

			SimpleDateFormat dateFormat;
			if (isToday(cal)) {
				dateFormat = new SimpleDateFormat(context.getResources().getString(R.string.today_date_format), Locale.getDefault());
			} else {
				dateFormat = new SimpleDateFormat(format, Locale.getDefault());
			}

			return dateFormat.format(cal.getTime());
		} catch (NumberFormatException nfe) {
			return String.valueOf(timestamp);
		}
	}

	static boolean isToday(Calendar cal) {
		return isSameDay(cal, Calendar.getInstance());
	}

	static boolean isSameDay(Calendar cal1, Calendar cal2) {
		if (cal1 == null || cal2 == null) {
			return false;
		}

		return (cal1.get(Calendar.ERA) == cal2.get(Calendar.ERA) &&
				cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
				cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR));
	}

	public static boolean onKeyVolumeAdjust(int keyCode) {
		if (!((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
				&& (Hacks.needSoftvolume())|| Build.VERSION.SDK_INT >= 15)) {
			return false; // continue
		}

		if (!LinphoneService.isReady()) {
			Log.i("Couldn't change softvolume has service is not running");
			return true;
		} else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
			LinphoneManager.getInstance().adjustVolume(1);
		} else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
			LinphoneManager.getInstance().adjustVolume(-1);
		}
		return true;
	}





	public static Bitmap downloadBitmap(Uri uri) {
		URL url;
		InputStream is = null;
		try {
			url = new URL(uri.toString());
			is = url.openStream();
			return BitmapFactory.decodeStream(is);
		} catch (MalformedURLException e) {
			Log.e(e, e.getMessage());
		} catch (IOException e) {
			Log.e(e, e.getMessage());
		} finally {
			try {is.close();} catch (IOException x) {}
		}
		return null;
	}


	public static void setImagePictureFromUri(Context c, ImageView view, Uri pictureUri, Uri thumbnailUri) {
		if (pictureUri == null) {
			view.setImageResource(R.drawable.avatar);
			return;
		}
		if (pictureUri.getScheme().startsWith("http")) {
			Bitmap bm = downloadBitmap(pictureUri);
			if (bm == null) view.setImageResource(R.drawable.avatar);
			view.setImageBitmap(bm);
		} else {
			Bitmap bm = null;
			try {
				bm = Images.Media.getBitmap(c.getContentResolver(), pictureUri);
			} catch (IOException e) {
				if (thumbnailUri != null) {
					try {
						bm = Images.Media.getBitmap(c.getContentResolver(), thumbnailUri);
					} catch (IOException ie) {
					}
				}
			}
			if (bm != null) {
				view.setImageBitmap(bm);
			} else {
				view.setImageResource(R.drawable.avatar);
			}
		}
	}

	public static void setThumbnailPictureFromUri(Context c, ImageView view, Uri tUri) {
		if (tUri == null) {
			view.setImageResource(R.drawable.avatar);
			return;
		}
		if (tUri.getScheme().startsWith("http")) {
			Bitmap bm = downloadBitmap(tUri);
			if (bm == null) view.setImageResource(R.drawable.avatar);
			view.setImageBitmap(bm);
		} else {
			Bitmap bm = null;
			try {
				bm = Images.Media.getBitmap(c.getContentResolver(),tUri);
			} catch (IOException e) {
				view.setImageURI(tUri);
			}
			if(bm != null) {
				view.setImageBitmap(bm);
			}
		}
	}

	public static final List<LinphoneCall> getLinphoneCallsNotInConf(LinphoneCore lc) {
		List<LinphoneCall> l=new ArrayList<LinphoneCall>();
		for(LinphoneCall c : lc.getCalls()){
			if (!c.isInConference()){
				l.add(c);
			}
		}
		return l;
	}

	public static final List<LinphoneCall> getLinphoneCallsInConf(LinphoneCore lc) {
		List<LinphoneCall> l=new ArrayList<LinphoneCall>();
		for(LinphoneCall c : lc.getCalls()){
			if (c.isInConference()){
				l.add(c);
			}
		}
		return l;
	}

	public static final List<LinphoneCall> getLinphoneCalls(LinphoneCore lc) {
		// return a modifiable list
		return new ArrayList<LinphoneCall>(Arrays.asList(lc.getCalls()));
	}

	public static final boolean hasExistingResumeableCall(LinphoneCore lc) {
		for (LinphoneCall c : getLinphoneCalls(lc)) {
			if (c.getState() == State.Paused) {
				return true;
			}
		}
		return false;
	}

	public static final List<LinphoneCall> getCallsInState(LinphoneCore lc, Collection<State> states) {
		List<LinphoneCall> foundCalls = new ArrayList<LinphoneCall>();
		for (LinphoneCall call : getLinphoneCalls(lc)) {
			if (states.contains(call.getState())) {
				foundCalls.add(call);
			}
		}
		return foundCalls;
	}
	public static final List<LinphoneCall> getRunningOrPausedCalls(LinphoneCore lc) {
		return getCallsInState(lc, Arrays.asList(
				State.Paused,
				State.PausedByRemote,
				State.StreamsRunning));
	}

	public static final int countConferenceCalls(LinphoneCore lc) {
		int count = lc.getConferenceSize();
		if (lc.isInConference()) count--;
		return count;
	}

	public static int countVirtualCalls(LinphoneCore lc) {
		return lc.getCallsNb() - countConferenceCalls(lc);
	}
	public static int countNonConferenceCalls(LinphoneCore lc) {
		return lc.getCallsNb() - countConferenceCalls(lc);
	}

	public static void setVisibility(View v, int id, boolean visible) {
		v.findViewById(id).setVisibility(visible ? VISIBLE : GONE);
	}
	public static void setVisibility(View v, boolean visible) {
		v.setVisibility(visible ? VISIBLE : GONE);
	}
	public static void enableView(View root, int id, OnClickListener l, boolean enable) {
		View v = root.findViewById(id);
		v.setVisibility(enable ? VISIBLE : GONE);
		v.setOnClickListener(l);
	}

	public static int pixelsToDpi(Resources res, int pixels) {
		return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, (float) pixels, res.getDisplayMetrics());
	}

	public static boolean isCallRunning(LinphoneCall call)
	{
		if (call == null) {
			return false;
		}

		LinphoneCall.State state = call.getState();

		return state == LinphoneCall.State.Connected ||
				state == LinphoneCall.State.CallUpdating ||
				state == LinphoneCall.State.CallUpdatedByRemote ||
				state == LinphoneCall.State.StreamsRunning ||
				state == LinphoneCall.State.Resuming;
	}

	public static boolean isCallEstablished(LinphoneCall call) {
		if (call == null) {
			return false;
		}

		LinphoneCall.State state = call.getState();

		return isCallRunning(call) ||
				state == LinphoneCall.State.Paused ||
				state == LinphoneCall.State.PausedByRemote ||
				state == LinphoneCall.State.Pausing;
	}

	public static boolean isHighBandwidthConnection(Context context){
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        return (info != null && info.isConnected() && isConnectionFast(info.getType(),info.getSubtype()));
    }

	private static boolean isConnectionFast(int type, int subType){
		if (type == ConnectivityManager.TYPE_MOBILE) {
            switch (subType) {
            case TelephonyManager.NETWORK_TYPE_EDGE:
            case TelephonyManager.NETWORK_TYPE_GPRS:
            case TelephonyManager.NETWORK_TYPE_IDEN:
            	return false;
            }
		}
        //in doubt, assume connection is good.
        return true;
    }

	public static void clearLogs() {
		try {
			Runtime.getRuntime().exec(new String[] { "logcat", "-c" });
		} catch (IOException e) {
			Log.e(e);
		}
	}

    public static boolean zipLogs(StringBuilder sb, String toZipFile){
        boolean success = false;
        try {
            FileOutputStream zip = new FileOutputStream(toZipFile);

            ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(zip));
            ZipEntry entry = new ZipEntry("logs.txt");
            out.putNextEntry(entry);

            out.write(sb.toString().getBytes());

            out.close();
            success = true;

        } catch (Exception e){
            Log.e("Exception when trying to zip the logs: " + e.getMessage());
        }

        return success;
    }

	public static void collectLogs(Context context, String email) {
        BufferedReader br = null;
        Process p = null;
        StringBuilder sb = new StringBuilder();

    	try {
			p = Runtime.getRuntime().exec(new String[] { "logcat", "-d", "|", "grep", "`adb shell ps | grep " + context.getPackageName() + " | cut -c10-15`" });
	    	br = new BufferedReader(new InputStreamReader(p.getInputStream()), 2048);

            String line;
	    	while ((line = br.readLine()) != null) {
	    		sb.append(line);
	    		sb.append("\r\n");
	    	}
            String zipFilePath = context.getExternalFilesDir(null).getAbsolutePath() + "/logs.zip";
            Log.i("Saving logs to " + zipFilePath);

            if( zipLogs(sb, zipFilePath) ) {
            	final String appName = (context != null) ? context.getString(R.string.app_name) : "Linphone(?)";

                Uri zipURI = Uri.parse("file://" + zipFilePath);
                Intent i = new Intent(Intent.ACTION_SEND);
                i.putExtra(Intent.EXTRA_EMAIL, new String[]{email});
                i.putExtra(Intent.EXTRA_SUBJECT, appName + " Logs");
                i.putExtra(Intent.EXTRA_TEXT, appName + " logs");
                i.setType("application/zip");
                i.putExtra(Intent.EXTRA_STREAM, zipURI);
                try {
                    context.startActivity(Intent.createChooser(i, "Send mail..."));
                } catch (android.content.ActivityNotFoundException ex) {

                }
            }

		} catch (IOException e) {
			Log.e(e);
		}
	}

	public static String getExtensionFromFileName(String fileName) {
		String extension = null;
		int i = fileName.lastIndexOf('.');
		if (i > 0) {
		    extension = fileName.substring(i+1);
		}
		return extension;
	}

	public static void recursiveFileRemoval(File root) {
		if (!root.delete()) {
			if (root.isDirectory()) {
				File[] files = root.listFiles();
		        if (files != null) {
		            for (File f : files) {
		            	recursiveFileRemoval(f);
		            }
		        }
			}
		}
	}

	public static String getDisplayableUsernameFromAddress(String sipAddress) {
		String username = sipAddress;
		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc == null) return username;

		if (username.startsWith("sip:")) {
			username = username.substring(4);
		}

		if (username.contains("@")) {
			String domain = username.split("@")[1];
			LinphoneProxyConfig lpc = lc.getDefaultProxyConfig();
			if (lpc != null) {
				if (domain.equals(lpc.getDomain())) {
					return username.split("@")[0];
				}
			} else {
				if (domain.equals(LinphoneManager.getInstance().getContext().getString(R.string.default_domain))) {
					return username.split("@")[0];
				}
			}
		}
		return username;
	}

	public static String getFullAddressFromUsername(String username) {
		String sipAddress = username;
		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc == null) return sipAddress;

		if (!sipAddress.startsWith("sip:")) {
			sipAddress = "sip:" + sipAddress;
		}

		if (!sipAddress.contains("@")) {
			LinphoneProxyConfig lpc = lc.getDefaultProxyConfig();
			if (lpc != null) {
				sipAddress = sipAddress + "@" + lpc.getDomain();
			} else {
				sipAddress = sipAddress + "@" + LinphoneManager.getInstance().getContext().getString(R.string.default_domain);
			}
		}
		return sipAddress;
	}

	public static void storeImage(Context context, LinphoneChatMessage msg) {
		if (msg == null || msg.getFileTransferInformation() == null || msg.getAppData() == null) return;
		File file = new File(Environment.getExternalStorageDirectory(), msg.getAppData());
		Bitmap bm = BitmapFactory.decodeFile(file.getPath());
		if (bm == null) return;

		ContentValues values = new ContentValues();
        values.put(Images.Media.TITLE, file.getName());
        String extension = msg.getFileTransferInformation().getSubtype();
        values.put(Images.Media.MIME_TYPE, "image/" + extension);
        ContentResolver cr = context.getContentResolver();
        Uri path = cr.insert(Images.Media.EXTERNAL_CONTENT_URI, values);

        OutputStream stream;
		try {
			stream = cr.openOutputStream(path);
			if (extension != null && extension.toLowerCase(Locale.getDefault()).equals("png")) {
				bm.compress(Bitmap.CompressFormat.PNG, 100, stream);
			} else {
				bm.compress(Bitmap.CompressFormat.JPEG, 100, stream);
			}

			stream.close();
			file.delete();
	        bm.recycle();

	        msg.setAppData(path.toString());
		} catch (FileNotFoundException e) {
			Log.e(e);
		} catch (IOException e) {
			Log.e(e);
		}
	}

	private static Context getContext() {
		if (context == null && LinphoneManager.isInstanciated())
			context = LinphoneManager.getInstance().getContext();
		return context;
	}

	public static void displayError(boolean isOk, TextView error, String errorText) {
		if (isOk) {
			error.setVisibility(View.INVISIBLE);
			error.setText("");
		} else {
			error.setVisibility(View.VISIBLE);
			error.setText(errorText);
		}
	}

	public static String errorForStatus(LinphoneAccountCreator.Status status) {
		Context ctxt = getContext();
		if (ctxt != null) {
			if (status.equals(LinphoneAccountCreator.Status.EmailInvalid))
				return ctxt.getString(R.string.invalid_email);
			if (status.equals(LinphoneAccountCreator.Status.UsernameInvalid))
				return ctxt.getString(R.string.invalid_username);
			if (status.equals(LinphoneAccountCreator.Status.UsernameTooShort))
				return ctxt.getString(R.string.username_too_short);
			if (status.equals(LinphoneAccountCreator.Status.UsernameTooLong))
				return ctxt.getString(R.string.username_too_long);
			if (status.equals(LinphoneAccountCreator.Status.UsernameInvalidSize))
				return ctxt.getString(R.string.username_invalid_size);
			if (status.equals(LinphoneAccountCreator.Status.PhoneNumberTooShort))
				return ctxt.getString(R.string.phone_number_too_short);
			if (status.equals(LinphoneAccountCreator.Status.PhoneNumberTooLong))
				return ctxt.getString(R.string.phone_number_too_long);
			if (status.equals(LinphoneAccountCreator.Status.PhoneNumberInvalid))
				return ctxt.getString(R.string.phone_number_invalid);
			if (status.equals(LinphoneAccountCreator.Status.PasswordTooShort))
				return ctxt.getString(R.string.password_too_short);
			if (status.equals(LinphoneAccountCreator.Status.PasswordTooLong))
				return ctxt.getString(R.string.password_too_long);
			if (status.equals(LinphoneAccountCreator.Status.DomainInvalid))
				return ctxt.getString(R.string.invalid_domain);
			if (status.equals(LinphoneAccountCreator.Status.RouteInvalid))
				return ctxt.getString(R.string.invalid_route);
			if (status.equals(LinphoneAccountCreator.Status.DisplayNameInvalid))
				return ctxt.getString(R.string.invalid_display_name);
			if (status.equals(LinphoneAccountCreator.Status.Failed))
				return ctxt.getString(R.string.request_failed);
			if (status.equals(LinphoneAccountCreator.Status.ErrorServer))
				return ctxt.getString(R.string.wizard_failed);
			if (status.equals(LinphoneAccountCreator.Status.TransportNotSupported))
				return ctxt.getString(R.string.transport_unsupported);
			if (status.equals(LinphoneAccountCreator.Status.AccountExist)
					|| status.equals(LinphoneAccountCreator.Status.AccountExistWithAlias))
				return ctxt.getString(R.string.account_already_exist);
			if (status.equals(LinphoneAccountCreator.Status.CountryCodeInvalid))
				return ctxt.getString(R.string.country_code_invalid);
			if (status.equals(LinphoneAccountCreator.Status.PhoneNumberUsedAccount)
					|| status.equals(LinphoneAccountCreator.Status.PhoneNumberUsedAlias))
				return ctxt.getString(R.string.assistant_phone_number_unavailable);
			if (status.equals(LinphoneAccountCreator.Status.AccountNotExist))
				return ctxt.getString(R.string.assistant_error_bad_credentials);
			if (status.equals(LinphoneAccountCreator.Status.PhoneNumberNotUsed))
				return ctxt.getString(R.string.phone_number_not_exist);
			if (status.equals(LinphoneAccountCreator.Status.PhoneNumberNotUsed)
					|| status.equals(LinphoneAccountCreator.Status.AccountNotActivated)
					|| status.equals(LinphoneAccountCreator.Status.AccountAlreadyActivated)
					|| status.equals(LinphoneAccountCreator.Status.AccountActivated)
					|| status.equals(LinphoneAccountCreator.Status.AccountNotCreated)
					|| status.equals(LinphoneAccountCreator.Status.Ok))
				return "";
		}
		return null;
	}

	public static String getCountryCode(EditText dialCode) {
		if(dialCode != null) {
			String code = dialCode.getText().toString();
			if(code != null && code.startsWith("+")) {
				code = code.substring(1);
			}
			return code;
		}
		return null;
	}

	public static void setCountry(DialPlan c, EditText dialCode, Button selectCountry, int countryCode) {
		if( c != null && dialCode != null && selectCountry != null) {
			dialCode.setText(c.getCountryCode());
			selectCountry.setText(c.getCountryName());
		} else {
			if(countryCode != -1){
				dialCode.setText("+" + countryCode);
			} else {
				dialCode.setText("+");
			}
		}
	}

	public static void displayErrorAlert(String msg, Context ctxt) {
		if (ctxt != null) {
			AlertDialog.Builder builder = new AlertDialog.Builder(ctxt);
			builder.setMessage(msg)
					.setCancelable(false)
					.setNeutralButton("Ok", null)
					.show();
		}
	}
}

