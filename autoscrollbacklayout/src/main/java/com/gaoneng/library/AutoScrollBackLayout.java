package com.gaoneng.library;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.annotation.AttrRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;

/**
 * 列表显示自动返回顶部的包装类
 * Created by gaoneng on 17-6-6.
 */
public class AutoScrollBackLayout extends FrameLayout {
    public static final String TAG = "AutoScrollBackLayout";
    private static final int DELAYMILLIS = 1500;
    public boolean DEBUG = false;
    private View wrapView;
    private ImageView scrollBackView;
    private boolean mShowScroll;
    private Animation mShowAnimation;
    private Animation mHideAnimation;
    private int scroll_gravity;
    private MyScrollLitener myScrollLitener;
    private DismissRunable dismissRunable;
    private int showScrollDistance;
    private int arrowIcon;

    public AutoScrollBackLayout(@NonNull Context context) {
        super(context);
        init(context, null, 0);
    }

    public AutoScrollBackLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0);
    }

    public AutoScrollBackLayout(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr);
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr) {
        if (isInEditMode()) return;
        TypedArray attr = context.obtainStyledAttributes(attrs, R.styleable.AutoScrollBackLayout, defStyleAttr, 0);
        mShowScroll = attr.getBoolean(R.styleable.AutoScrollBackLayout_show_scroll, true);
        int showAnimResourceId = attr.getResourceId(R.styleable.AutoScrollBackLayout_show_animation, R.anim.fab_scale_up);
        mShowAnimation = AnimationUtils.loadAnimation(getContext(), showAnimResourceId);
        int hideAnimResourceId = attr.getResourceId(R.styleable.AutoScrollBackLayout_hide_animation, R.anim.fab_scale_down);
        mHideAnimation = AnimationUtils.loadAnimation(getContext(), hideAnimResourceId);
        scroll_gravity = attr.getInt(R.styleable.AutoScrollBackLayout_scroll_gravity, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        showScrollDistance = attr.getDimensionPixelSize(R.styleable.AutoScrollBackLayout_scroll_distance, 100);
        arrowIcon = attr.getResourceId(R.styleable.AutoScrollBackLayout_auto_arrow_icon, R.drawable.go_top);
        attr.recycle();
        myScrollLitener = new MyScrollLitener();
        dismissRunable = new DismissRunable();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        attachAbsListView();
        attachScrollBackView();
    }

    private void attachAbsListView() {
        if (getChildCount() > 0) {
            wrapView = findTargetScrollView(this);
            if (DEBUG) {
                Log.i(TAG, "find wrapView=" + (wrapView == null ? null : wrapView.getClass().getName()));
            }
        }
    }

    private View findTargetScrollView(View view) {
        if (view != null) {
            if (view instanceof AbsListView || view instanceof RecyclerView) return view;
            if (view instanceof ViewGroup) {
                View target = null;
                ViewGroup viewGroup = (ViewGroup) view;
                for (int i = 0; i < viewGroup.getChildCount(); i++) {
                    target = findTargetScrollView(viewGroup.getChildAt(i));
                }
                return target;
            }
        }
        return null;
    }

    private void attachScrollBackView() {
        scrollBackView = new ImageView(getContext());
        scrollBackView.setImageResource(arrowIcon);
        if (mShowScroll) {
            scrollBackView.setVisibility(INVISIBLE);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            params.gravity = scroll_gravity;
            params.bottomMargin = DensityUtils.dp2px(getContext(), 16f);
            scrollBackView.setOnClickListener(new PendingScrollBackListener());
            addView(scrollBackView, params);
        }
    }

    /**
     * 如果已经使用<code>AbsListView.setOnScrollListener()</code>设置过监听，一定要在其后面调用；
     * 如果没有使用<code>AbsListView.setOnScrollListener()</code>，就在onCreate()或者onActivityCreated()中调用即可;
     */
    public void bindScrollBack() {
        if (wrapView != null && scrollBackView != null && mShowScroll) {
            if (wrapView instanceof AbsListView) {
                hookScrollListenerForAbsListview();
            } else if (wrapView instanceof RecyclerView) {
                addScrollListenerForRecyclerView();
            }
        }
    }

    private void hookScrollListenerForAbsListview() {
        try {
            //通过反射获取mOnScrollListener对象
            Field scrollListenerField = AbsListView.class.getDeclaredField("mOnScrollListener");
            scrollListenerField.setAccessible(true);
            Object object = scrollListenerField.get(wrapView);
            //需要被代理的目前对象
            AbsListView.OnScrollListener target;
            if (object == null) {
                //如果mOnScrollListener没有设置过，就设置一个空的用来hook
                target = new FakeScrollLitener();
            } else {
                target = (AbsListView.OnScrollListener) object;
            }
            //InvocationHandler对象，用于添加额外的控制处理
            ScrollListenerInvocationHandler listenerInvocationHandler = new ScrollListenerInvocationHandler(target);
            //Proxy.newProxyInstance生成动态代理对象
            AbsListView.OnScrollListener proxy = listenerInvocationHandler.getProxy();
            if (DEBUG) {
                Log.i(TAG, "target=" + target.getClass().getName() + " ,proxy=" + proxy.getClass().getName() + ", proxied interfaces=" + Arrays.toString(proxy.getClass().getInterfaces()));
            }
            //将代理对象proxy设置到被反射的mOnScrollListener的字段中
            scrollListenerField.set(wrapView, proxy);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    private void addScrollListenerForRecyclerView() {
        ((RecyclerView) wrapView).addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (myScrollLitener != null)
                    myScrollLitener.onScrollStateChanged(recyclerView, newState);
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (myScrollLitener != null) myScrollLitener.onScroll(recyclerView, dy, 0, 0);
            }
        });
    }

    /**
     * 都是采取获取当前第一个可见视图的位置，来计算该视图到顶部的距离，这样计算的滚动距离都是假定每个行高是固定的情况；
     *
     * @param dy RecyclerView中onscroll()回传的dy，但是在移除或者添加数据后就不准确，这里还是作为一个备选值
     */
    private int getScrollDistance(int dy) {
        if (wrapView == null) return 0;
        if (wrapView instanceof AbsListView) {
            AbsListView listView = (AbsListView) wrapView;
            View topChild = listView.getChildAt(0);
            return topChild == null ? 0 : listView.getFirstVisiblePosition() * topChild.getHeight() - topChild.getTop();
        } else if (wrapView instanceof RecyclerView) {
            RecyclerView recyclerView = (RecyclerView) this.wrapView;
            RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
            if (layoutManager instanceof LinearLayoutManager) {
                LinearLayoutManager linearLayoutManager = (LinearLayoutManager) layoutManager;
                View fristChild = recyclerView.getChildAt(0);
                if (fristChild == null) return 0;
                int top = fristChild.getTop();
                int firstVisibleItemPosition = linearLayoutManager.findFirstVisibleItemPosition();
                return -top + firstVisibleItemPosition * fristChild.getHeight();
            }
            return dy;
        }
        return 0;
    }

    private void playShowAnimation() {
        mHideAnimation.cancel();
        if (scrollBackView != null) {
            scrollBackView.startAnimation(mShowAnimation);
        }
    }

    private void playHideAnimation() {
        mShowAnimation.cancel();
        if (scrollBackView != null) {
            scrollBackView.startAnimation(mHideAnimation);
        }
    }

    public boolean isHidden() {
        return scrollBackView != null && scrollBackView.getVisibility() == INVISIBLE;
    }

    public void show(boolean animate) {
        if (isHidden()) {
            if (animate) {
                playShowAnimation();
            }
            if (scrollBackView != null) {
                scrollBackView.setVisibility(VISIBLE);
            }
        }
    }

    public void hide(boolean animate) {
        if (!isHidden()) {
            if (animate) {
                playHideAnimation();
            }
            if (scrollBackView != null) {
                scrollBackView.setVisibility(INVISIBLE);
            }
        }
    }

    public void toggle(boolean animate) {
        if (isHidden()) {
            show(animate);
        } else {
            hide(animate);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (dismissRunable != null) {
            removeCallbacks(dismissRunable);
        }
        if (scrollBackView != null) {
            scrollBackView.clearAnimation();
        }
    }

    private class PendingScrollBackListener implements OnClickListener {

        @Override
        public void onClick(final View v) {
            //点击返回到顶部列表，悬停3秒后消失
            ScrollUtil.smoothScrollListViewToTop(wrapView, new ScrollUtil.ScrollToTopListener() {
                @Override
                public void scrollComplete() {
                    v.post(dismissRunable);
                }
            });
        }
    }

    private class DismissRunable implements Runnable {
        @Override
        public void run() {
            hide(true);
        }
    }

    private class MyScrollLitener {
        private int scrollState;
        private int scrollDistance;

        /**
         * RecyclerView.SCROLL_STATE_IDLE=0 <br/>
         * AbsListView.OnScrollListener.SCROLL_STATE_IDLE=0 <br/>
         * scrollState=0即为停止滚动
         */
        void onScrollStateChanged(View scrollview, int scrollState) {
            //列表滚动停止时,此时根据滚动距离判断是否需要延迟隐藏返回按钮
            this.scrollState = scrollState;
            String logTarget = scrollview.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(scrollview));
            if (DEBUG) {
                Log.d(TAG, logTarget + " scrollState=" + scrollState + ",scrollDistance=" + scrollDistance + ",showScrollDistance=" + showScrollDistance);
            }
            if (scrollState == 0) {
                if (DEBUG) {
                    Log.d(TAG, logTarget + " hide()!!! scrollState=" + 0 + ",scrollDistance=" + scrollDistance + ",showScrollDistance=" + showScrollDistance);
                }
                if (dismissRunable != null) {
                    removeCallbacks(dismissRunable);
                    postDelayed(dismissRunable, scrollDistance >= showScrollDistance ? DELAYMILLIS : 0);
                }
            }
        }

        void onScroll(View scrollview, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            if (scrollState == 0) return;
            scrollDistance = getScrollDistance(firstVisibleItem);
            String logTarget = scrollview.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(scrollview));
            if (scrollDistance >= showScrollDistance) {
                if (DEBUG) {
                    Log.d(TAG, logTarget + " show()!!! scrollState=" + scrollState + ",scrollDistance=" + scrollDistance + ",firstVisibleItem=" + firstVisibleItem);
                }
                if (dismissRunable != null) {
                    removeCallbacks(dismissRunable);
                    postDelayed(dismissRunable, DELAYMILLIS);
                }
                show(true);
            }
        }
    }

    private class ScrollListenerInvocationHandler implements InvocationHandler {
        private AbsListView.OnScrollListener target;

        ScrollListenerInvocationHandler(AbsListView.OnScrollListener target) {
            this.target = target;
        }

        public AbsListView.OnScrollListener getProxy() {
            // Proxy.newProxyInstance() 第二个参数一定不能使用 target.getClass().getInterfaces()获得被代理的接口
            // 因为上面获得的是当前实现类本身实现的接口，不包含父类实现的接口；
            // 这里采取固定AbsListView.OnScrollListener接口的方式即可;
            return (AbsListView.OnScrollListener) Proxy.newProxyInstance(target.getClass().getClassLoader(), new Class[]{AbsListView.OnScrollListener.class}, this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (DEBUG) {
                Log.i(TAG, "动态代理拦截  method=" + method.getName());
            }
            if (myScrollLitener != null) {
                if (method.getName().equals("onScroll")) {
                    myScrollLitener.onScroll((View) args[0], (int) args[1], (int) args[2], (int) args[3]);
                } else if (method.getName().equals("onScrollStateChanged")) {
                    myScrollLitener.onScrollStateChanged((View) args[0], (int) args[1]);
                }
            }
            return method.invoke(target, args);
        }
    }

    private class FakeScrollLitener implements AbsListView.OnScrollListener {
        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {

        }

        @Override
        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {

        }
    }
}
