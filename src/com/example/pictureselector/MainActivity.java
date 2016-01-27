package com.example.pictureselector;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.GridView;
import android.widget.PopupWindow.OnDismissListener;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.pictureselector.ListImageDirPopupWindow.OnDirSelectedListener;
import com.example.pictureselector.bean.FolderBean;
import com.example.pictureselector.bean.ImageAdapter;

public class MainActivity extends Activity {	
	private static final int DATA_LOADED=0x110;
	
	private GridView mGridView;
	private List<String> mImgs;
	private ImageAdapter mImgAdapter;

	private RelativeLayout mBottonLy;
	private TextView mDirName;
	private TextView mDirCount;

	private File mCurrentDir;
	private int mMaxCount;

	private List<FolderBean> mFolderBeans = new ArrayList<FolderBean>();

	private ProgressDialog mProgressDialog;
	
	private ListImageDirPopupWindow mPopupWindow;
	
	private Handler mHandler=new Handler() {
		public void handleMessage(android.os.Message msg) {
			if(msg.what==DATA_LOADED) {
				mProgressDialog.dismiss();
				
				//绑定数据到View中
				data2View();
				
				initPopuWindow();
				
				Log.d("测试3", "mHandler");
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		initView();
		initDatas();
		initEvent();
	}

	protected void data2View() {
		if(mCurrentDir==null) {
			Toast.makeText(this, "未扫描到任何图片", Toast.LENGTH_SHORT).show();
			return;
		}
		
		mImgs=Arrays.asList(mCurrentDir.list(new FilenameFilter() {
			
			@Override
			public boolean accept(File dir, String filename) {
				if(filename.endsWith(".jpg")||filename.endsWith(".jpeg")||filename.endsWith(".png"))
					return true;
				return false;
			}
		}));
		mImgAdapter=new ImageAdapter(this, mImgs, mCurrentDir.getAbsolutePath());
		mGridView.setAdapter(mImgAdapter);
		
		mDirCount.setText(""+mMaxCount);
		mDirName.setText(mCurrentDir.getName());
	}

	
	private void initEvent() {
		mBottonLy.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				mPopupWindow.setAnimationStyle(R.style.dir_popupwindow_anim);//设置popupWindow显示与收起来的动画
				mPopupWindow.showAsDropDown(mBottonLy, 0, 0);//设置显示PopupWindow的位置位于指定View的左下方，x,y表示坐标偏移量
				
				lightOff();
			}
		});
	}
	
	private void initPopuWindow() {
		mPopupWindow=new ListImageDirPopupWindow(this, mFolderBeans);
		
		//在popupWindow出现 时候，会有屏幕变暗的特效，所以要在这里设置popupWindow消失的时候的事件，使屏幕变回原来的亮度
		mPopupWindow.setOnDismissListener(new OnDismissListener() {
			
			@Override
			public void onDismiss() {
				lightOn();
			}
		});
		
		mPopupWindow.setOnDirSelectedListener(new OnDirSelectedListener() {
			@Override
			public void onSeleted(FolderBean folderBean) {
				mCurrentDir=new File(folderBean.getDir());
				
				//由于mImgs每次都是根据已有文件夹来搜索图片然后传入GridView的适配器中，
				//所以在已有文件夹下增删图片之后重新在popupWindow中打开该文件夹是能够刷新数据的
				//而由于所有有图片的文件夹是在程序一开启的时候扫描完成的，
				//所以之后如果不重启程序而新增文件夹且在文件夹下增加图片是不会被扫描的，除非重启程序
				//或者在未关闭程序的时候删除已经扫描的到的文件夹，然后再程序中从PopupWindow中选择该文件夹(因为此时ListView的数据还没刷新所以还暂时存在于PopupWindow中)会因异常退出
				mImgs=Arrays.asList(mCurrentDir.list(new FilenameFilter() {
					
					@Override
					public boolean accept(File dir, String filename) {
						if(filename.endsWith(".jpg")||filename.endsWith(".jpeg")||filename.endsWith(".png"))
							return true;
						return false;
					}
				}));				
				mImgAdapter=new ImageAdapter(MainActivity.this, mImgs, folderBean.getDir());
				mGridView.setAdapter(mImgAdapter);
				
				mDirCount.setText(mImgs.size()+"");
				mDirName.setText(folderBean.getName());
				
				mPopupWindow.dismiss();
				
				//如果将程序挂在后台，然后在程序打开已经存在的目录进行增删图片，重进程序GridView显示的内容是不会更新的，
				//如果重新打开该目录所对应的文件，GridView显示内容会更新，但是PopupWindow中的ListView的文件夹下图片的数量不会更新
				//所以为了更新ListView的数据，需要加上下面的代码
				//且因为ListDirAdapter适配器中的getView方法（ListView一旦有变动就会调用getView重绘）有设置ListView的文件夹下图片的数量，
				//所以不需要另外调用ListDirAdapter实例的notifyDataSetChanged方法刷新数据
				for(FolderBean fb:mFolderBeans) {
					Log.d("测试2", fb.getDir().substring(fb.getDir().lastIndexOf("/")+1)+" "+mCurrentDir.getName());
					if(fb.getDir().substring(fb.getDir().lastIndexOf("/")+1).equals(mCurrentDir.getName())) {
						if(fb.getCount()!=mImgs.size())
							fb.setCount(mImgs.size());
					}
				}
				
				for(int i=0;i<mFolderBeans.size();i++)
				Log.d("测试", mFolderBeans.get(i).getName()+""+mFolderBeans.get(i).getCount());
			}
		});
	}
	
	/**
	 * 内容区域变暗
	 */
	private void lightOff() {
		WindowManager.LayoutParams lp=getWindow().getAttributes();//Attributes-属性
		lp.alpha=0.3f;
		getWindow().setAttributes(lp);
	}
	
	/**
	 * 内容区域变亮
	 */
	private void lightOn() {
		WindowManager.LayoutParams lp=getWindow().getAttributes();//Attributes-属性
		lp.alpha=1.0f;
		getWindow().setAttributes(lp);
	}

	/**
	 * 利用ContentProvider扫描手机中的所有图片
	 */
	private void initDatas() {
		if (!Environment.getExternalStorageState().equals(
				Environment.MEDIA_MOUNTED)) {
			Toast.makeText(this, "当前存储卡不可用！", Toast.LENGTH_SHORT).show();
			return;
		}

		mProgressDialog = ProgressDialog.show(this, null, "Loading...");

		new Thread() {
			public void run() {			
				Uri mImgUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
				//Media.EXTERNAL_CONTENT_URI——The content:// style URI for the "primary" external storage volume，primary：原始的，第一位
				//MediaStore这个类是android系统提供的一个多媒体数据库
				
				ContentResolver cr = MainActivity.this.getContentResolver();//内容提供器

				//这里需要注意"=? or "的空格不能丢
				Cursor cursor=cr.query(mImgUri, null, MediaStore.Images.Media.MIME_TYPE
						+ "=? or " + MediaStore.Images.Media.MIME_TYPE + "=?",
						new String[] { "image/jpeg", "image/png" },
						MediaStore.Images.Media.DATE_MODIFIED);//最后一个是排序方式，以图片的日期作为依据
				
				Set<String> mDirPaths=new HashSet<String>();//用于存储包含图片的文件夹的路径
				
				while(cursor.moveToNext()) {
					String path=cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
					File parentFile=new File(path).getParentFile();
					if(parentFile==null) continue;//这里需要判断一下，有时候parentFile会出现为null的情况，虽然不知道具体原因，有可能是因为图片被隐藏的原因
					
					String dirPath=parentFile.getAbsolutePath();//得到绝对路径
					
					if(mDirPaths.contains(dirPath)) continue;//防止重复遍历相同文件夹下的图片
					else {
						mDirPaths.add(dirPath);
						FolderBean folderBean=new FolderBean();
						folderBean.setDir(dirPath);
						folderBean.setFirstImgPath(path);
						
						if(parentFile.list()==null) continue;
						//parentFile.list()：返回这个文件所代表的目录中的文件名的字符串数组。如果这个文件不是一个目录，结果是空的
						
						int picSize=parentFile.list(new FilenameFilter() {
							//设置过滤，防止非图片被计算
							@Override
							public boolean accept(File dir, String filename) {
								if(filename.endsWith(".jpg")||filename.endsWith(".jpeg")||filename.endsWith(".png"))
									return true;
							return false;
							}
						}).length;
						
						folderBean.setCount(picSize);
						
						mFolderBeans.add(folderBean);
						
						if(picSize>mMaxCount) {
							mMaxCount=picSize;
							mCurrentDir=parentFile;
						}
					}		
				}
				cursor.close();
				
				//通知Handler扫描图片完成
				mHandler.sendEmptyMessage(DATA_LOADED);
			}
		}.start();
	}

	private void initView() {
		mGridView = (GridView) findViewById(R.id.id_gridview);
		mBottonLy = (RelativeLayout) findViewById(R.id.id_bottom_ly);
		mDirName = (TextView) findViewById(R.id.id_dir_name);
		mDirCount = (TextView) findViewById(R.id.id_dir_count);
	}
}
