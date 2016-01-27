package com.example.pictureselector.bean;

public class FolderBean {
	/**
	 * 当前文件夹的路径
	 */
	private String dir;
	private String firstImgPath;
	/**
	 * 当前文件夹的名称
	 */
	private String name;
	/**
	 * 当前文件夹中图片的数量
	 */
	private int count;
	
	public FolderBean() {
		
	}

	public String getDir() {
		return dir;
	}

	public void setDir(String dir) {
		this.dir = dir;
		
		int lastIndexOf=this.dir.lastIndexOf("/");
		this.name=this.dir.substring(lastIndexOf+1);
	}

	public String getFirstImgPath() {
		return firstImgPath;
	}

	public void setFirstImgPath(String firstImgPath) {
		this.firstImgPath = firstImgPath;
	}

	public int getCount() {
		return count;
	}

	public void setCount(int count) {
		this.count = count;
	}
	
	public String getName() {
		return name;
	}
}
