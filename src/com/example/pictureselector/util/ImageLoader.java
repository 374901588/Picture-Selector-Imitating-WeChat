package com.example.pictureselector.util;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.LruCache;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;

/**
 * 图片加载类 （采用单例模式）
 * 
 * @author Just
 * 
 */
public class ImageLoader {

	private static ImageLoader mInstance;

	// 成员变量
	/**
	 * 图片缓存的核心对象
	 */
	private LruCache<String, Bitmap> mLruCache;// String表示图片的路径
	/**
	 * 线程池，用于统一处理Task （ImageLoader中的存在一个后台线程来取任务然后加入到线程池中）
	 */
	private ExecutorService mThreadPool;
	private static final int DEAFULT_THREAD_COUNT = 1;// 线程池的默认线程数
	/**
	 * 队列的调度方式 （即用来记录图片的加载策略，默认为LIFO）
	 */
	private Type mType = Type.LIFO;
	/**
	 * 任务队列，供线程池取任务
	 */
	private LinkedList<Runnable> mTaskQueue;
	/**
	 * 后台轮询线程
	 */
	private Thread mPoolThread;
	private Handler mPoolThreadHandler;// 是与上面定义的线程绑定在一起的，用于给线程发送消息
	/**
	 * UI线程中的Handler 用于传入一个Task以后，当图片获取成功以后，
	 * 用mUIHandler发送消息，为图片设置回调（回调显示图片的Bitmap）
	 */
	private Handler mUIHandler;

	/**
	 * Semaphore(信号量)通常用于限制可以访问某些资源（物理或逻辑的）的线程数目。
	 * 通过信号量控制mPoolThread（后台轮询线程）中初始化mPoolThreadHandler与
	 * 使用的loadImage（loadImage中的调用了addTask（addTask中使用了mPoolThraedhandler））的线程的顺序
	 */
	private Semaphore mSemaphorePoolThreadHandler = new Semaphore(0);// 初始化时设置可用的许可证数量为0，使得当addTask在mPoolThreadHandler初始化之前执行时处于等待状态

	private Semaphore mSemaphoreThreadPool;// 再init()中根据线程数量指定信号量并初始化

	public enum Type {
		FIFO, LIFO;
	}

	private ImageLoader(int mThreadCount, Type type) {// 可以由用户决定线程池的线程数以及队列的调度方式
		init(mThreadCount, type);
	}

	/**
	 * 初始化
	 * @param mThreadCount
	 * @param type
	 */
	private void init(int mThreadCount, Type type) {
		
		// 后台轮询线程，这里涉及的Looper可以阅读这篇博客 http://blog.csdn.net/lmj623565791/article/details/38377229
		mPoolThread = new Thread() {
			@Override
			public void run() {
				Looper.prepare();//Looper.prepare()是在后台轮询线程中调用的
				// Looper用于封装了android线程中的消息循环，默认情况下一个线程是不存在消息循环（message loop）的，
				// 需要调用Looper.prepare()来给线程创建一个消息循环，调用Looper.loop()来使消息循环起作用，
				// 从消息队列里取消息，处理消息。
				

				// "找不到 url -> 创建一个Task -> 将Task放入TaskQueue且发送一个通知去提醒后台轮询线程"
				// 如果来了一个任务，mPoolThreadHandler会发送一个Message到Looper中，最终会调用handleMessage
				mPoolThreadHandler = new Handler() {
					@Override
					public void handleMessage(Message msg) {
						// 线程池取出任务进行执行
						mThreadPool.execute(getTask());

						try {
							mSemaphoreThreadPool.acquire();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				};
				// 发布一个许可证，从而可以唤醒addTask的等待
				mSemaphorePoolThreadHandler.release();
				Looper.loop();
			}
		};

		mPoolThread.start();

		int maxMemory = (int) Runtime.getRuntime().maxMemory();// 获取应用最大可用内存
		int cacheMemory = maxMemory / 8;// 用于初始化mLruCache
		mLruCache = new LruCache<String, Bitmap>(cacheMemory) {
			@Override
			protected int sizeOf(String key, Bitmap value) {// 测量每个Bitmap所占据的内存并返回
				return value.getRowBytes() * value.getHeight();// 每行占据的字节数*高度
			}
		};

		// 创建线程池
		mThreadPool = Executors.newFixedThreadPool(mThreadCount);
		mTaskQueue = new LinkedList<Runnable>();
		mType = type;

		mSemaphoreThreadPool = new Semaphore(mThreadCount);
	}

	/**
	 * 从任务队列取出一个Runnable
	 * 
	 * @return
	 */
	private Runnable getTask() {
		if (mType == Type.FIFO) {
			return mTaskQueue.removeFirst();
		} else if (mType == Type.LIFO) {
			return mTaskQueue.removeLast();
		}
		return null;
	}

	public static ImageLoader getInstance() {
		// 外层的if判断不做同步处理，但是可以过滤掉大部分的代码，当mInstance初始化后基本上if体里面的代码就不要执行了
		// 但是在刚开始mInstance未初始化的时候，因未作同步的处理，可能会有一两个线程同时到达if体里面
		// 等到达里面后再做synchronized处理，这样会使得需要做同步的线程减少，只有一开始的几个
		if (mInstance == null) {
			// 就是这个点可能会有几个线程同时到达
			synchronized (ImageLoader.class) {
				// 里层的if判断是必要的，因为在外层if判断之后的那个点可能会有几个线程同时到达，
				// 接着在进行synchronized，如果不加判断可能会new出几个实例
				if (mInstance == null)
					mInstance = new ImageLoader(DEAFULT_THREAD_COUNT, Type.LIFO);
			}
		}

		// 上面的这种处理相比于public synchronized static ImageLoader getInstance()提高了效率

		return mInstance;
	}

	public static ImageLoader getInstance(int threadCount, Type type) {
		if (mInstance == null) {
			synchronized (ImageLoader.class) {
				if (mInstance == null)
					mInstance = new ImageLoader(threadCount, type);
			}
		}

		return mInstance;
	}

	/**
	 * 根据path为imageView设置图片
	 * 
	 * @param path
	 * @param imageView
	 */
	public void loadImage(final String path, final ImageView imageView) {
		imageView.setTag(path);// 防止item复用的时候，imageView复用造成图片错乱，imageView设置图片时会根据Tag对比path
		//这里可以参考一下http://blog.csdn.net/lmj623565791/article/details/24333277这篇博客

		if (mUIHandler == null) {
			mUIHandler = new Handler() {
				@Override
				public void handleMessage(Message msg) {
					// 获取得到的图片，为imageView回调设置图片
					ImgBeanHolder holder = (ImgBeanHolder) msg.obj;
					Bitmap b = holder.bitmap;
					ImageView iv = holder.imageView;
					String p = holder.path;
					// 将path与getTag存储路径进行比较

					if (iv.getTag().toString().equals(p)) {
						iv.setImageBitmap(b);
					}
				}
			};
		}

		// 根据path在缓存中获取bitmap
		Bitmap bm = getBitmapFromLruCache(path);
		if (bm != null) {
			refreashBitmap(path, imageView, bm);
		} else {
			// Task的任务就是压缩图片，然后将图片加入到缓存之中，然后回调图片（即将图片载入到指定的imageView中）
			addTask(new Runnable() {
				@Override
				public void run() {
					// 加载图片，涉及图片的压缩
					// 1、获得图片需要显示的大小（即刚好为imageView的大小）
					ImageSize imageSize = getImageViewSize(imageView);
					// 2、压缩图片
					Bitmap b = decodeSampledBitmapFromPath(path,
							imageSize.width, imageSize.height);
					// 将图片加入到缓存中
					addBitmapToLruCache(path, b);

					refreashBitmap(path, imageView, b);

					mSemaphoreThreadPool.release();// 当任务一旦执行完，就释放一个许可证
				}
			});
		}
	}

	private void refreashBitmap(final String path, final ImageView imageView,
			Bitmap b) {
		Message message = Message.obtain();// 从整个Messge池中返回一个新的Message实例，避免分配新的对象，减少内存开销
		ImgBeanHolder holder = new ImgBeanHolder();
		holder.bitmap = b;
		holder.path = path;// loadImage的形参
		holder.imageView = imageView;// loadImage的形参
		message.obj = holder;
		mUIHandler.sendMessage(message);
	}

	/**
	 * 将图片加入到LruCache
	 * 
	 * @param path
	 * @param b
	 */
	private void addBitmapToLruCache(String path, Bitmap b) {
		if (getBitmapFromLruCache(path) == null) {// 需要判断缓存中是否已经存在
			if (b != null) {
				mLruCache.put(path, b);
			}
		}
	}

	/**
	 * 根据图片需要的显示的宽和高进行压缩
	 * 
	 * @param path
	 * @param width
	 * @param height
	 * @return
	 */
	private Bitmap decodeSampledBitmapFromPath(String path, int width,
			int height) {
		// 获取图片实际的宽和高，并不把图片加载到内存中
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;// 使得图片不加载到内存中，但是options.outWidth和options.outHeight会存在值
		BitmapFactory.decodeFile(path, options);// options中会包含图片实际的宽和高（即options.outWidth和options.outHeight）
		options.inSampleSize = caculateInSampleSize(options, width, height);

		// 使用获取到的inSampleSize再次解析图片并且加载到内存中
		options.inJustDecodeBounds = false;
		Bitmap bitmap = BitmapFactory.decodeFile(path, options);

		return bitmap;
	}

	/**
	 * 根据需求的宽和高以及实际的宽和高计算SampleSize（压缩比例）
	 * 
	 * @param options
	 * @param width
	 * @param height
	 * @return
	 */
	private int caculateInSampleSize(Options options, int reqWidth,
			int reqHeight) {
		int width = options.outWidth;
		int height = options.outHeight;

		int inSampleSize = 1;

		// 这儿只是一个普通的压缩策略，当然也可以自己定制特别的压缩策略
		if (width > reqWidth || height > reqHeight) {
			int widthRadio = Math.round(width * 1.0f / reqWidth);// 将比例四舍五入
			int heightRadio = Math.round(height * 1.0f / reqHeight);

			inSampleSize = Math.max(widthRadio, heightRadio);// 为了图片不失帧，保持原比例一般取小值，但是得到的图片会始终比显示区域大一些，这里为了节省内存，所以尽量的压缩
		}

		return inSampleSize;
	}

	/**
	 * 根据ImageVIew获取适当的压缩的宽和高
	 * 
	 * @param imageView
	 * @return
	 */
	// @SuppressLint("NewApi")
	// 在Android代码中，我们有时会使用比我们在AndroidManifest中设置的android:minSdkVersion版本更高的方法，
	// 此时编译器会提示警告，解决方法是在方法上加上@SuppressLint("NewApi"）或者@TargetApi()
	// SuppressLint("NewApi")作用仅仅是屏蔽android lint错误，所以在方法中还要判断版本做不同的操作
	private ImageSize getImageViewSize(ImageView imageView) {
		ImageSize imageSize = new ImageSize();

		DisplayMetrics displayMetrics = imageView.getContext().getResources()
				.getDisplayMetrics();

		// imageView在布局中的大小可能是固定大小的，也有可能是相对的

		LayoutParams lp = imageView.getLayoutParams();

		int width = imageView.getWidth();// 获取imageView的实际宽度
		// 有可能ImageView刚被new出来而没有添加到容器中，或者其他原因，导致无法获取真正的width，因此需要判断一下
		if (width <= 0) {
			width = lp.width;// 设置为在布局中声明的宽度，但是有可能在布局中是wrap_content(-1)，match_parent(-2),所以需要下一步的判断
		}
		if (width <= 0) {
			// 赋值为最大值,但是如果没有设置的话，width依然是获取不到的理想的值的，因此还需要下一步判断

			// getMaxWidth方法是在API16中才有，还需要处理一下，利用反射获取,以便兼容到16以下的版本
			// width=imageView.getMaxWidth();
			width = getImageViewFieldValue(imageView, "mMaxWidth");// "mMaxWidth"可以去ImageView的源码中查看
		}
		if (width <= 0) {
			width = displayMetrics.widthPixels;// 最后没办法，只能等于屏幕的宽度
		}

		int height = imageView.getHeight();
		if (height <= 0) {
			height = lp.height;
		}
		if (height <= 0) {
			// height=imageView.getMaxHeight();
			height = getImageViewFieldValue(imageView, "mMaxHeight");
		}
		if (height <= 0) {
			height = displayMetrics.heightPixels;
		}

		imageSize.width = width;
		imageSize.height = height;

		return imageSize;
	}

	/**
	 * 通过反射获取ImageView的某个属性值
	 * 
	 * @param object
	 * @param fieldName
	 * @return
	 */
	public static int getImageViewFieldValue(ImageView object, String fieldName) {
		int value = 0;

		try {
			Field field = ImageView.class.getDeclaredField(fieldName);
			// Field 提供有关类或接口的单个字段的信息，以及对它的动态访问权限。反射的字段可能是一个类（静态）字段或实例字段。
			// getDeclaredField返回指定字段的字段对象

			field.setAccessible(true);// 在反射使用中,如果字段是私有的,那么必须要对这个字段设置

			int fieldValue = field.getInt(object);// 因为包括指定类的所有实例的指定字段(除静态字段)，所以在这里需要指定实例对象

			if (fieldValue > 0 && fieldValue < Integer.MAX_VALUE) {
				value = fieldValue;
			}

		} catch (Exception e) {
		}

		return value;
	}

	/**
	 * addTask需要同步(synchronized)，避免多个线程造成mSemaphorePoolThreadHandler.acquire()
	 * 从而导致死锁的状态 且mTaskQueue.add(runnable)本身也需要同步
	 * 
	 * @param runnable
	 */
	private synchronized void addTask(Runnable runnable) {
		mTaskQueue.add(runnable);

		try {
			if (mPoolThreadHandler == null)
				mSemaphorePoolThreadHandler.acquire();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		mPoolThreadHandler.sendEmptyMessage(0x110);
	}

	/**
	 * 根据path在缓存中获取bitmap
	 * 
	 * @param key
	 * @return
	 */
	private Bitmap getBitmapFromLruCache(String key) {
		return mLruCache.get(key);
	}

	private class ImageSize {
		public int width;
		public int height;
	}

	private class ImgBeanHolder {
		public Bitmap bitmap;
		public ImageView imageView;
		public String path;
	}
}
