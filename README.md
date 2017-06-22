# AutoScrollBackLayout
在ListView或者RecyclerView列表滚动向底部一段距离，就自动显示一个返回顶部的按钮

## 效果
![](https://github.com/gaoneng102/AutoScrollBackLayout/blob/master/preview.gif)

## 使用
1、通过xml文件添加如下：
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
2、调用bindScrollBack()：
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
- 针对ListView,通过反射和动态代理的方式监听OnScrollListener，这样就不会影响已有的OnScrollListener的正常运行。
**但是这里需要注意的是，如果已经使用`ListView.setOnScrollListener()`设置过监听，
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
