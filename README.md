# JsonEntityGenerator
An Android Studio Plugin to generate java file from json
一个用于将 json 转换为 java 类的 IDEA 插件

## 特性
1. 支持格式化 json
2. 支持 json 格式校验
3. json 内支持注释(// or  /* */)
4. 支持将 json 内的注释转录到 java 类中(但 json 格式化后，在行尾的注释会跑到下一行)
5. 支持将原 json 内的值以注释的方式插入到类字段属性后, 便于对照
6. 类的属性支持 private 和 public 访问权限
7. json 内多个对象的字段名以及字段类型相同，只创建一个类
8. 只有源码目录打开 New 菜单，才会显示 Class From Json 选项

<img src="https://raw.githubusercontent.com/wangkunlin/JsonEntityGenerator/master/art/screenshot_0.png" />
<img src="https://raw.githubusercontent.com/wangkunlin/JsonEntityGenerator/master/art/screenshot_1.png" />
<img src="https://raw.githubusercontent.com/wangkunlin/JsonEntityGenerator/master/art/screenshot_2.png" />
