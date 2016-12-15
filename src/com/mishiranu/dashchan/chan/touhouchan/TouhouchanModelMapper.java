package com.mishiranu.dashchan.chan.touhouchan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import chan.content.model.FileAttachment;
import chan.content.model.Icon;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.util.CommonUtils;
import chan.util.StringUtils;

public class TouhouchanModelMapper {
	private static final Pattern PATTERN_PRE = Pattern.compile("<pre>(.*?)</pre>");

	public static FileAttachment createFileAttachment(JSONObject jsonObject, TouhouchanChanLocator locator,
			String boardName) throws JSONException {
		FileAttachment attachment = new FileAttachment();
		String image = CommonUtils.getJsonString(jsonObject, "image");
		String thumbnail = CommonUtils.getJsonString(jsonObject, "thumbnail");
		String originalName = CommonUtils.optJsonString(jsonObject, "origname");
		attachment.setSize(jsonObject.optInt("size"));
		attachment.setWidth(jsonObject.optInt("width"));
		attachment.setHeight(jsonObject.optInt("height"));
		attachment.setFileUri(locator, locator.buildPath(boardName, image));
		attachment.setThumbnailUri(locator, locator.buildPath(boardName, thumbnail));
		attachment.setOriginalName(originalName);
		return attachment;
	}

	public static Post createPost(JSONObject jsonObject, TouhouchanChanLocator locator, String boardName)
			throws JSONException {
		Post post = new Post();
		if (jsonObject.optInt("sticky") != 0) {
			post.setSticky(true);
		}
		if (jsonObject.optInt("locked") != 0) {
			post.setClosed(true);
		}
		if (jsonObject.optInt("banned") != 0) {
			post.setPosterBanned(true);
		}
		String num = CommonUtils.getJsonString(jsonObject, "num");
		String parent = CommonUtils.getJsonString(jsonObject, "parent");
		post.setPostNumber(num);
		if (!"0".equals(parent)) {
			post.setParentPostNumber(parent);
		}
		post.setTimestamp(jsonObject.getLong("timestamp") * 1000L);
		String name = CommonUtils.optJsonString(jsonObject, "name");
		if (name != null) {
			name = StringUtils.nullIfEmpty(StringUtils.clearHtml(name).trim());
			post.setName(name);
		}
		String trip = CommonUtils.optJsonString(jsonObject, "trip");
		if (trip != null) {
			trip = StringUtils.nullIfEmpty(StringUtils.clearHtml(trip).trim());
			post.setTripcode(trip);
		}
		String id = CommonUtils.optJsonString(jsonObject, "id");
		if (id != null) {
			id = StringUtils.nullIfEmpty(StringUtils.clearHtml(id).trim());
			post.setIdentifier(id);
		}
		int capcode = jsonObject.optInt("adminpost");
		if (capcode == 1) {
			post.setCapcode("Team");
		} else if (capcode == 2) {
			post.setCapcode("Mod");
		}
		String email = CommonUtils.optJsonString(jsonObject, "email");
		if (!StringUtils.isEmpty(email) && email.equalsIgnoreCase("mailto:sage")) {
			post.setSage(true);
		}
		String subject = CommonUtils.optJsonString(jsonObject, "subject");
		if (subject != null) {
			subject = StringUtils.nullIfEmpty(StringUtils.clearHtml(subject).trim());
			post.setSubject(subject);
		}
		String comment = CommonUtils.optJsonString(jsonObject, "comment");
		if (comment != null) {
			// Replace <br> within <pre>
			StringBuffer buffer = null;
			Matcher matcher = PATTERN_PRE.matcher(comment);
			while (matcher.find()) {
				if (buffer == null) {
					buffer = new StringBuffer();
				}
				String text = matcher.group(1);
				text = text.replace("<br>", "\n");
				matcher.appendReplacement(buffer, "<pre>" + text + "</pre>");
			}
			if (buffer != null) {
				matcher.appendTail(buffer);
				comment = buffer.toString();
			}
			post.setComment(comment);
		}
		if (!StringUtils.isEmpty(CommonUtils.optJsonString(jsonObject, "image"))) {
			try {
				post.setAttachments(createFileAttachment(jsonObject, locator, boardName));
			} catch (JSONException e) {
				// Ignore exception
			}
		}
		return post;
	}

	public static Post[] createPosts(JSONArray jsonArray, TouhouchanChanLocator locator, String boardName)
			throws JSONException {
		if (jsonArray.length() > 0) {
			Post[] posts = new Post[jsonArray.length()];
			for (int i = 0; i < posts.length; i++) {
				posts[i] = createPost(jsonArray.getJSONObject(i), locator, boardName);
			}
			return posts;
		}
		return null;
	}

	public static Post[] createPosts(JSONObject jsonObject, TouhouchanChanLocator locator, String boardName)
			throws JSONException {
		JSONArray jsonArray = jsonObject.optJSONArray("data");
		if (jsonArray != null) {
			return createPosts(jsonArray, locator, boardName);
		} else {
			return null;
		}
	}

	public static Posts createThread(JSONObject jsonObject, TouhouchanChanLocator locator, String boardName)
			throws JSONException {
		int postsCount = jsonObject.optInt("omit");
		int filesCount = jsonObject.optInt("omitimages");
		JSONArray jsonArray = jsonObject.getJSONArray("posts");
		Post[] posts = createPosts(jsonArray, locator, boardName);
		if (posts != null) {
			postsCount += posts.length;
			for (Post post : posts) {
				filesCount += post.getAttachmentsCount();
			}
			return new Posts(posts).addPostsCount(postsCount).addFilesCount(filesCount);
		}
		return null;
	}

	public static Posts[] createThreads(JSONObject jsonObject, TouhouchanChanLocator locator, String boardName)
			throws JSONException {
		JSONArray jsonArray = jsonObject.optJSONArray("data");
		if (jsonArray != null && jsonArray.length() > 0) {
			Posts[] threads = new Posts[jsonArray.length()];
			for (int i = 0; i < threads.length; i++) {
				threads[i] = createThread(jsonArray.getJSONObject(i), locator, boardName);
			}
			return threads;
		}
		return null;
	}
}