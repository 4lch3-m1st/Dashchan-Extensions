package com.mishiranu.dashchan.chan.arhivach;

import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;

import chan.content.ApiException;
import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.content.ChanPerformer;
import chan.content.InvalidResponseException;
import chan.content.model.Post;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;
import chan.http.HttpResponse;
import chan.http.UrlEncodedEntity;
import chan.text.ParseException;
import chan.util.CommonUtils;

public class ArhivachChanPerformer extends ChanPerformer
{
	public static final int PAGE_SIZE = 25;
	
	@Override
	public ReadThreadsResult onReadThreads(ReadThreadsData data) throws HttpException, InvalidResponseException
	{
		ArhivachChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.buildPath("index/" + (data.pageNumber * PAGE_SIZE));
		String responseText = new HttpRequest(uri, data.holder, data).setValidator(data.validator).read().getString();
		try
		{
			return new ReadThreadsResult(new ArhivachThreadsParser(responseText, this, true).convertThreads());
		}
		catch (ParseException e)
		{
			throw new InvalidResponseException(e);
		}
	}
	
	@Override
	public ReadPostsResult onReadPosts(ReadPostsData data) throws HttpException, InvalidResponseException
	{
		ArhivachChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.createThreadUri(null, data.threadNumber);
		String responseText = new HttpRequest(uri, data.holder, data).setValidator(data.validator).read().getString();
		try
		{
			return new ReadPostsResult(new ArhivachPostsParser(responseText, this, data.threadNumber).convert());
		}
		catch (ParseException e)
		{
			throw new InvalidResponseException(e);
		}
	}
	
	@Override
	public ReadSearchPostsResult onReadSearchPosts(ReadSearchPostsData data) throws HttpException,
			InvalidResponseException
	{
		ArhivachChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.buildQuery("ajax", "callback", "", "act", "tagcomplete", "q", data.searchQuery);
		String responseText = new HttpRequest(uri, data.holder, data).read().getString();
		if (responseText.length() <= 2) return null;
		responseText = responseText.substring(1, responseText.length() - 1);
		ArrayList<String> tags = new ArrayList<>();
		try
		{
			JSONObject jsonObject = new JSONObject(responseText);
			JSONArray jsonArray = jsonObject.getJSONArray("tags");
			for (int i = 0; i < jsonArray.length(); i++)
			{
				jsonObject = jsonArray.getJSONObject(i);
				String id = CommonUtils.getJsonString(jsonObject, "id");
				tags.add(id);
			}
		}
		catch (JSONException e)
		{
			throw new InvalidResponseException(e);
		}
		if (tags.size() > 0)
		{
			ArrayList<Post> posts = new ArrayList<>();
			for (int i = 0, maxTags = Math.min(tags.size(), 5); i < maxTags; i++)
			{
				uri = locator.buildQuery("index", "tags", tags.get(i));
				try
				{
					responseText = new HttpRequest(uri, data.holder, data).read().getString();
				}
				catch (HttpException e)
				{
					if (e.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND) break;
					throw e;
				}
				try
				{
					ArrayList<Post> result = new ArhivachThreadsParser(responseText, this, false).convertPosts();
					if (result == null || result.isEmpty()) break;
					if (posts == null) posts = new ArrayList<>();
					posts.addAll(result);
				}
				catch (ParseException e)
				{
					throw new InvalidResponseException(e);
				}
			}
			return new ReadSearchPostsResult(posts);
		}
		return null;
	}
	
	private boolean isBlack(int[] line)
	{
		for (int i = 0; i < line.length; i++)
		{
			int color = line[i];
			// With noise handling
			if (Color.red(color) > 0x30 || Color.green(color) > 0x30 || Color.blue(color) > 0x30) return false;
		}
		return true;
	}
	
	@Override
	public ReadContentResult onReadContent(ReadContentData data) throws HttpException, InvalidResponseException
	{
		if ("abload.de".equals(data.uri.getAuthority()) && data.uri.getPath().startsWith("/thumb/"))
		{
			HttpResponse response = new HttpRequest(data.uri, data.holder, data).read();
			try
			{
				Thread thread = Thread.currentThread();
				Bitmap bitmap = response.getBitmap();
				if (bitmap != null && bitmap.getWidth() == 132 && bitmap.getHeight() == 147)
				{
					int top = 1;
					int[] line = new int[130];
					for (int i = 2; i <= 130; i++)
					{
						bitmap.getPixels(line, 0, 130, 1, i, 130, 1);
						if (isBlack(line)) top = i + 1; else break;
					}
					if (thread.isInterrupted()) return null;
					int bottom = 130;
					for (int i = 130; i >= 1; i--)
					{
						bitmap.getPixels(line, 0, 130, 1, i, 130, 1);
						if (isBlack(line)) bottom = i - 1; else break;
					}
					if (thread.isInterrupted()) return null;
					int left = 1;
					for (int i = 1; i <= 130; i++)
					{
						bitmap.getPixels(line, 0, 1, i, 1, 1, 130);
						if (isBlack(line)) left = i + 1; else break;
					}
					if (thread.isInterrupted()) return null;
					int right = 130;
					for (int i = 130; i >= 1; i--)
					{
						bitmap.getPixels(line, 0, 1, i, 1, 1, 130);
						if (isBlack(line)) right = i - 1; else break;
					}
					if (thread.isInterrupted()) return null;
					top = Math.min(top, 132 - bottom);
					bottom = 132 - top;
					left = Math.min(left, 132 - right);
					right = 132 - left;
					Bitmap newBitmap = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top);
					bitmap.recycle();
					ByteArrayOutputStream stream = new ByteArrayOutputStream();
					newBitmap.compress(Bitmap.CompressFormat.JPEG, 40, stream);
					newBitmap.recycle();
					return new ReadContentResult(new HttpResponse(stream.toByteArray()));
				}
			}
			catch (Exception e)
			{
				
			}
			return new ReadContentResult(response);
		}
		return super.onReadContent(data);
	}
	
	private String mUserEmailPassword;
	private String mUserCaptchaKey;
	
	private boolean checkEmailPassword(String email, String password)
	{
		return email == null && password == null && mUserEmailPassword == null ||
				(email + " " + password).equals(mUserEmailPassword);
	}
	
	@Override
	public CheckAuthorizationResult onCheckAuthorization(CheckAuthorizationData data) throws HttpException,
			InvalidResponseException
	{
		String email = data.authorizationData[0];
		String password = data.authorizationData[1];
		return new CheckAuthorizationResult(authorizeUser(data.holder, data, email, password));
	}
	
	private boolean authorizeUser(HttpHolder holder, HttpRequest.Preset preset, String email, String password)
			throws HttpException, InvalidResponseException
	{
		mUserEmailPassword = null;
		mUserCaptchaKey = null;
		ArhivachChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.buildPath("api", "add");
		JSONObject jsonObject = new HttpRequest(uri, holder, preset).setPostMethod(new UrlEncodedEntity("email", email,
				"pass", password)).read().getJsonObject();
		if (jsonObject == null) throw new InvalidResponseException();
		return updateAuthorizationData(jsonObject, email, password);
	}
	
	private boolean updateAuthorizationData(JSONObject jsonObject, String email, String password)
	{
		JSONArray jsonArray = jsonObject.optJSONArray("info_msg");
		mUserCaptchaKey = CommonUtils.optJsonString(jsonObject, "captcha_public_key");
		if (jsonArray != null)
		{
			for (int i = 0; i < jsonArray.length(); i++)
			{
				String message = jsonArray.optString(i);
				if (message != null && message.contains("Вход выполнен"))
				{
					mUserEmailPassword = email + " " + password;
					return true;
				}
			}
		}
		mUserEmailPassword = null;
		return false;
	}
	
	@Override
	public ReadCaptchaResult onReadCaptcha(ReadCaptchaData data) throws HttpException, InvalidResponseException
	{
		CaptchaData captchaData = new CaptchaData();
		captchaData.put(CaptchaData.API_KEY, mUserCaptchaKey);
		return new ReadCaptchaResult(CaptchaState.CAPTCHA, captchaData);
	}
	
	@Override
	public SendAddToArchiveResult onSendAddToArchive(SendAddToArchiveData data) throws HttpException, ApiException,
			InvalidResponseException
	{
		String[] authorizationData = ChanConfiguration.get(this).getUserAuthorizationData();
		String email = authorizationData[0];
		String password = authorizationData[1];
		if (!checkEmailPassword(email, password))
		{
			if (email != null && password != null) authorizeUser(data.holder, data, email, password); else
			{
				mUserEmailPassword = null;
				mUserCaptchaKey = null;
			}
		}
		ArhivachChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.buildPath("api", "add");
		boolean first = true;
		OUTER: while (true)
		{
			String captchaChallenge = null;
			String captchaInput = null;
			if (mUserCaptchaKey != null)
			{
				CaptchaData captchaData = requireUserCaptcha(null, null, null, !first);
				if (captchaData == null) throw new ApiException(ApiException.ARCHIVE_ERROR_NO_ACCESS);
				captchaChallenge = captchaData.get(CaptchaData.CHALLENGE);
				captchaInput = captchaData.get(CaptchaData.INPUT);
				first = false;
			}
			UrlEncodedEntity entity = new UrlEncodedEntity();
			if (mUserEmailPassword != null)
			{
				entity.add("email", email);
				entity.add("pass", password);
			}
			entity.add("thread_url", data.uri.toString());
			entity.add("recaptcha_challenge_field", captchaChallenge);
			entity.add("recaptcha_response_field", captchaInput);
			entity.add("add_collapsed", data.options.contains("collapsed") ? "on" : null);
			entity.add("save_image_bytoken", data.options.contains("bytoken") ? "on" : null);
			JSONObject jsonObject = new HttpRequest(uri, data.holder, data).setPostMethod(entity)
					.read().getJsonObject();
			if (jsonObject == null) throw new InvalidResponseException();
			updateAuthorizationData(jsonObject, email, password);
			JSONArray errorsArray = jsonObject.optJSONArray("errors");
			String errorMessage = null;
			if (errorsArray != null)
			{
				for (int i = 0; i < errorsArray.length(); i++)
				{
					String message = errorsArray.optString(i);
					if (message != null)
					{
						if (message.contains("Ошибка ввода капчи"))
						{
							if (mUserCaptchaKey == null) throw new InvalidResponseException();
							continue OUTER;
						}
						else if (message.contains("Вы достигли лимита"))
						{
							throw new ApiException(ApiException.ARCHIVE_ERROR_TOO_OFTEN);
						}
						else if (!message.contains("Неверная пара")) errorMessage = message;
					}
				}
			}
			String threadUriString = CommonUtils.optJsonString(jsonObject, "added_thread_url");
			if (threadUriString != null)
			{
				uri = Uri.parse(threadUriString);
				String threadNumber = locator.getThreadNumber(uri);
				return new SendAddToArchiveResult(null, threadNumber);
			}
			if (errorMessage != null) throw new ApiException(errorMessage);
			break;
		}
		throw new InvalidResponseException();
	}
}