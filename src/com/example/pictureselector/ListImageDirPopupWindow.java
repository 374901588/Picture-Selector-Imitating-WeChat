package com.example.pictureselector;

import java.util.List;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.example.pictureselector.bean.FolderBean;
import com.example.pictureselector.util.ImageLoader;
import com.example.pictureselector.util.ImageLoader.Type;

public class ListImageDirPopupWindow extends PopupWindow {
	private int mWidth;
	private int mHeight;
	private View mConVertView;
	private ListView mListView;
	private List<FolderBean> mDatas;
	
	public OnDirSelectedListener mListener;
	
	public void setOnDirSelectedListener(OnDirSelectedListener mListener) {
		this.mListener = mListener;
	}

	public interface OnDirSelectedListener {
		void onSeleted(FolderBean folderBean);
	}

	public ListImageDirPopupWindow(Context context, List<FolderBean> mDatas) {
		calWidthAndHeight(context);
		Log.d("测试", "new PopupWindow()");
		
		mConVertView = LayoutInflater.from(context).inflate(
				R.layout.popup_main, null);
		this.mDatas = mDatas;

		setContentView(mConVertView);
		setWidth(mWidth);
		setHeight(mHeight);

		setFocusable(true);// 使得窗口可以从当前焦点小部件中抓取焦点
		setTouchable(true);// 可以点击
		setOutsideTouchable(true);// 可以点击范围之外的地方
		setBackgroundDrawable(new BitmapDrawable());

		setTouchInterceptor(new OnTouchListener() {

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
					dismiss();
					return true;
				}
				return false;
			}
		});

		initViews(context);
		initEvent();
	}

	private void initEvent() {
		mListView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				if(mListener!=null) {
					mListener.onSeleted(mDatas.get(position));
				}
			}
		});
	}

	private void initViews(Context context) {
		mListView = (ListView) mConVertView.findViewById(R.id.id_list_dir);
		mListView.setAdapter(new ListDirAdapter(context,mDatas));
	}

	/**
	 * 计算popupWindow的宽和高
	 * 
	 * @param context
	 */
	private void calWidthAndHeight(Context context) {
		WindowManager wm = (WindowManager) context
				.getSystemService(Context.WINDOW_SERVICE);
		DisplayMetrics outMetrics = new DisplayMetrics();
		wm.getDefaultDisplay().getMetrics(outMetrics);
		mWidth = outMetrics.widthPixels;
		mHeight = (int) (outMetrics.heightPixels * 0.7);
	}

	private class ListDirAdapter extends ArrayAdapter<FolderBean> {
		private LayoutInflater mInflater;
		private List<FolderBean> mDatas;

		public ListDirAdapter(Context context,List<FolderBean> objects) {
			super(context, 0, objects);

			mInflater = LayoutInflater.from(context);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder holder = null;

			if (holder == null) {
				holder = new ViewHolder();
				convertView = mInflater.inflate(R.layout.item_popup_main,
						parent, false);
				holder.mImg = (ImageView) convertView
						.findViewById(R.id.id_id_dir_item_image);
				holder.mDirName = (TextView) convertView
						.findViewById(R.id.id_dir_item_name);
				holder.mDirCount = (TextView) convertView
						.findViewById(R.id.id_dir_item_count);

				convertView.setTag(holder);
			} else
				holder = (ViewHolder) convertView.getTag();
			
			FolderBean bean=getItem(position);
			
			//重置
			holder.mImg.setImageResource(R.drawable.pictures_no);
			
			ImageLoader.getInstance().loadImage(bean.getFirstImgPath(), holder.mImg);
			
			holder.mDirCount.setText(""+bean.getCount());
			holder.mDirName.setText(bean.getName());

			return convertView;
		}

		private class ViewHolder {
			ImageView mImg;
			TextView mDirName;
			TextView mDirCount;
		}
	}
}
