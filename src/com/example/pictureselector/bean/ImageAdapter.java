package com.example.pictureselector.bean;

import java.util.HashSet;

import java.util.List;
import java.util.Set;
import android.content.Context;
import android.graphics.Color;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.example.pictureselector.R;
import com.example.pictureselector.util.ImageLoader;
import com.example.pictureselector.util.ImageLoader.Type;

public class ImageAdapter extends BaseAdapter {
	
	/**
	 * 记录图片是否被选中
	 * 当改变文件夹的时候，会重新new一个ImageAdapter
	 * 所以在这里用static，在不同的对象间共享数据
	 * 使得即使是改变了文件夹，当回到原来的文件夹时，已经被选中的图片不会被取消
	 */
	private static Set<String> mSelectedImg=new HashSet<String>();
	
	private String mDirPath;
	private List<String> mImagePaths;
	private LayoutInflater mInflater;//用于加载item的布局
	
	private int mScreenWidth;//屏幕的宽度
	
	/**
	 * @param context
	 * @param mDatas:传入的文件夹下所有图片的文件名
	 * @param dirPath：图片所在的文件夹的路径
	 * List存储的是图片的文件名而非图片的路径，如果图片比较多，存储路径的话浪费内存
	 */
	public ImageAdapter(Context context,List<String> mDatas,String dirPath) {		
		this.mDirPath=dirPath;
		mImagePaths=mDatas;
		mInflater=LayoutInflater.from(context);
		Log.d("ImageAdapter", "new ImageAdapter");
		
		WindowManager wm=(WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		DisplayMetrics outMetrics=new DisplayMetrics();
		wm.getDefaultDisplay().getMetrics(outMetrics);
		mScreenWidth=outMetrics.widthPixels;
	}

	@Override
	public int getCount() {
		return mImagePaths.size();
	}

	@Override
	public Object getItem(int position) {
		return mImagePaths.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(final int position, View convertView, ViewGroup parent) {
		
	    final ViewHolder viewHolder;
		if(convertView==null) {
			convertView=mInflater.inflate(R.layout.item_griview, parent,false);
			
			viewHolder=new ViewHolder();
			viewHolder.mImg=(ImageView) convertView.findViewById(R.id.id_item_image);
			viewHolder.mSelect=(ImageButton) convertView.findViewById(R.id.id_item_select);
			convertView.setTag(viewHolder);//使得下次可以直接getTag;
		}
		else {
			viewHolder=(ViewHolder) convertView.getTag();
		}
		
		//重置状态，防止第一屏的图片以及表示已经选择状态图标影响第二屏图片的显示
		//因为第二屏的item有可能是第一屏item的复用，而复用的item中的imageView还设置的是第一屏显示的图案
		//这里与imageView去setTag还是有所区别的
		viewHolder.mImg.setImageResource(R.drawable.pictures_no);
		viewHolder.mSelect.setImageResource(R.drawable.picture_unselected);
		viewHolder.mImg.setColorFilter(null);
		
		viewHolder.mImg.setMaxWidth(mScreenWidth/3);
		//因为已经在布局中设置一行显示三个图片，所以在这里可以设置mImg的MaxWidth（这行代码可选）
		//这样就可以优化ImageLoader中获取ImageView的宽度的那段代码（在某些情况下优化效果是比较明显的）
		
		ImageLoader.getInstance(3, Type.LIFO).loadImage(
				mDirPath + "/" + mImagePaths.get(position),viewHolder.mImg);
		
		final String filePath=mDirPath+"/"+mImagePaths.get(position);
		
		viewHolder.mImg.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				Log.d("viewHolder.mImg", "Clicked");
				
				//如果已经被选择
				if(mSelectedImg.contains(filePath)) {
					mSelectedImg.remove(filePath);
					viewHolder.mImg.setColorFilter(null);
					viewHolder.mSelect.setImageResource(R.drawable.picture_unselected);
				}
				else {
					mSelectedImg.add(filePath);
					viewHolder.mImg.setColorFilter(Color.parseColor("#77000000"));
					viewHolder.mSelect.setImageResource(R.drawable.pictures_selected);
				}
//				notifyDataSetChanged();
				//如果调用该方法会使得在点击屏幕的时候会出现闪屏的情况，所以直接在onClick中用set方法设置比较好，就不要调用该方法了
				//notifyDataSetChanged方法通过一个外部的方法控制如果适配器的内容改变时需要强制调用getView来刷新每个Item的内容
				//可以实现动态的刷新列表的功能
			}
		});
		
		//每次GridView的状态改变时(如滑动GridView)，或者选择另外一个文件夹，就需要调用getView来重绘视图
		//如果不加下面这段代码，GridView的状态改变时或者选择另外一个文件夹，原来已经被选择的图片就不会显示被选择的效果
		//但是实际上该图片已经被选择了
		if (mSelectedImg.contains(filePath)) {
			viewHolder.mImg.setColorFilter(Color
					.parseColor("#77000000"));
			viewHolder.mSelect
					.setImageResource(R.drawable.pictures_selected);
		}
		
		return convertView;//这里返回的就是GridView的item为item_griview.xml中布局的关键
	}
	
	
	private class ViewHolder {
		ImageView mImg;
		ImageButton mSelect;
	}
}
