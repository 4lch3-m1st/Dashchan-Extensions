package com.mishiranu.dashchan.chan.cablesix;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import chan.content.model.Board;
import chan.content.model.BoardCategory;
import chan.text.GroupParser;
import chan.text.ParseException;
import chan.util.StringUtils;

public class CableSixBoardsParser implements GroupParser.Callback
{
	private final String mSource;
	
	private final ArrayList<BoardCategory> mBoardCategories = new ArrayList<>();
	private final ArrayList<Board> mBoards = new ArrayList<>();
	
	private String mBoardCategoryTitle;
	
	private boolean mBoardListParsing = false;
	
	private static final Pattern PATTERN_BOARD_URI = Pattern.compile("/(.*?)/");
	
	public CableSixBoardsParser(String source)
	{
		mSource = source;
	}
	
	public ArrayList<BoardCategory> convert() throws ParseException
	{
		try
		{
			GroupParser.parse(mSource, this);
		}
		catch (FinishedException e)
		{
			
		}
		return mBoardCategories;
	}
	
	private void closeCategory()
	{
		ArrayList<Board> boards = mBoards;
		if (boards.size() > 0)
		{
			mBoardCategories.add(new BoardCategory(mBoardCategoryTitle, boards));
			mBoards.clear();
		}
	}
	
	private static class FinishedException extends ParseException
	{
		private static final long serialVersionUID = 1L;
	}
	
	@Override
	public boolean onStartElement(GroupParser parser, String tagName, String attrs)
	{
		if ("div".equals(tagName))
		{
			String cssClass = parser.getAttr(attrs, "class");
			if ("boardlist top".equals(cssClass)) mBoardListParsing = true;
		}
		else if (mBoardListParsing)
		{
			if ("span".equals(tagName))
			{
				String cssClass = parser.getAttr(attrs, "class");
				if (cssClass.equals("sub")) closeCategory();
				mBoardCategoryTitle = StringUtils.clearHtml(parser.getAttr(attrs, "data-description")).trim();
			}
			else if ("a".equals(tagName))
			{
				String href = parser.getAttr(attrs, "href");
				Matcher matcher = PATTERN_BOARD_URI.matcher(href);
				if (matcher.matches())
				{
					String title = StringUtils.clearHtml(parser.getAttr(attrs, "title")).trim();
					mBoards.add(new Board(matcher.group(1), title));
				}
			}
		}
		return false;
	}
	
	@Override
	public void onEndElement(GroupParser parser, String tagName) throws FinishedException
	{
		if ("div".equals(tagName) && mBoardListParsing)
		{
			closeCategory();
			throw new FinishedException();
		}
	}
	
	@Override
	public void onText(GroupParser parser, String source, int start, int end)
	{
		
	}
	
	@Override
	public void onGroupComplete(GroupParser parser, String text)
	{
		
	}
}