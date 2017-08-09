# AutoScrollBackLayout
在ListView，GridView，RecyclerView列表滚动向底部一段距离，就自动显示一个返回顶部的按钮

## 效果
![](https://github.com/gaoneng102/AutoScrollBackLayout/blob/master/preview.gif)

## 使用
1、添加依赖：
```
compile ('com.gaoneng.library:autoscrollbacklayout:1.1.1'){
        exclude group: 'com.android.support', module: 'recyclerview-v7'
    }
```
2、通过xml文件添加如下：
```
<?xml version="1.0" encoding="utf-8"?>
<com.gaoneng.library.AutoScrollBackLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/scroll_layout"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:paddingLeft="8dp"
    android:paddingRight="8dp"
    app:show_scroll="true">

    <ListView
        android:id="@android:id/list"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:layout_weight="1"
        android:drawSelectorOnTop="false" />
</com.gaoneng.library.AutoScrollBackLayout>
```
3、调用bindScrollBack()：
```
AutoScrollBackLayout autoScrollBackLayout = (AutoScrollBackLayout) findViewById(R.id.scroll_layout);
ListView listView = (ListView) findViewById(android.R.id.list);
List<String> list = new ArrayList<>();
for (int i = 0; i < 20; i++) {
    list.add("this is a test! in " + i);
}
listView.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, list));
autoScrollBackLayout.bindScrollBack();
```
## 原理
- 针对ListView和GridView,通过反射和动态代理的方式监听OnScrollListener，这样就不会影响已有的OnScrollListener的正常运行。
**但是这里需要注意的是，如果已经使用`AbsListView.setOnScrollListener()`设置过监听，
一定要在其后面调用`autoScrollBackLayout.bindScrollBack()`**
```
 private void hookScrollListenerForListview() {
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
```
- 针对RecyclerView，因为其内部的监听已经是`List<OnScrollListener>`形式，所以直接`addOnScrollListener()`方式添加即可；
```
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
```

## 其他属性
attrs | value
------------ | -------------
app:show_scroll | true/false 是否自动显示返回按钮，默认true
app:scroll_distance | dp 触发显示返回按钮的滚动距离，默认100dp
app:show_animation | anim 按钮出现动画，默认 R.anim.fab_scale_up
app:hide_animation | anim 按钮出现动画，默认 R.anim.fab_scale_down
app:auto_arrow_icon | drawable 按钮图标，默认 R.drawable.go_top
app:scroll_gravity | Gravity 按钮的位置，默认 Gravity.BOTTOM and Gravity.CENTER_HORIZONTAL



