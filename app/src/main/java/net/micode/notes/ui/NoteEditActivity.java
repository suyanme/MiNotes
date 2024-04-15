import android.app.PendingIntent;
import android.app.SearchManager;
import android.appwidget.AppWidgetManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.ImageSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
@@ -47,6 +58,7 @@
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
@@ -65,6 +77,7 @@
import net.micode.notes.widget.NoteWidgetProvider_2x;
import net.micode.notes.widget.NoteWidgetProvider_4x;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
@@ -154,6 +167,7 @@ private class HeadViewHolder {
    private String mUserQuery;
    private Pattern mPattern;

    private final int PHOTO_REQUEST = 1;//请求码
    // 在Activity被创建时调用的方法
    // Activity的创建。设置布局，初始化状态和资源
    @Override
@@ -166,6 +180,25 @@ protected void onCreate(Bundle savedInstanceState) {
            return;
        }
        initResources();
        //添加部分0406
        //根据id获取添加图片按钮
        final ImageButton add_img_btn = (ImageButton) findViewById(R.id.add_img_btn);
        //为点击图片按钮设置监听器
        add_img_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "onClick: click add image button");
                //ACTION_GET_CONTENT: 允许用户选择特殊种类的数据，并返回（特殊种类的数据：照一张相片或录一段音）
                Intent loadImage = new Intent(Intent.ACTION_GET_CONTENT);
                //Category属性用于指定当前动作（Action）被执行的环境.
                //CATEGORY_OPENABLE; 用来指示一个ACTION_GET_CONTENT的intent
                loadImage.addCategory(Intent.CATEGORY_OPENABLE);
                loadImage.setType("image/*");

                startActivityForResult(loadImage, PHOTO_REQUEST);
            }
        });

    }

    /**
@@ -894,6 +927,54 @@ private String makeShortcutIconTitle(String content) {
        return content.length() > SHORTCUT_ICON_TITLE_MAX_LEN ? content.substring(0,
                SHORTCUT_ICON_TITLE_MAX_LEN) : content;
    }
    //重写onActivityResult()来处理返回的数据
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        ContentResolver resolver = getContentResolver();
        if (requestCode==PHOTO_REQUEST) {
//            case PHOTO_REQUEST:
            Uri originalUri = intent.getData(); //1.获得图片的真实路径
            Bitmap bitmap = null;
            try {
                bitmap = BitmapFactory.decodeStream(resolver.openInputStream(originalUri));//2.解码图片
            } catch (FileNotFoundException e) {
                Log.d(TAG, "onActivityResult: get file_exception");
                e.printStackTrace();
            }

            if (bitmap != null) {
                //3.根据Bitmap对象创建ImageSpan对象
                Log.d(TAG, "onActivityResult: bitmap is not null");
                ImageSpan imageSpan = new ImageSpan(NoteEditActivity.this, bitmap);
                String path = getPath(this, originalUri);
                //4.使用[local][/local]将path括起来，用于之后方便识别图片路径在note中的位置
                String img_fragment = "[local]" + path + "[/local]";
                //创建一个SpannableString对象，以便插入用ImageSpan对象封装的图像
                SpannableString spannableString = new SpannableString(img_fragment);
                spannableString.setSpan(imageSpan, 0, img_fragment.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                //5.将选择的图片追加到EditText中光标所在位置
                NoteEditText e = (NoteEditText) findViewById(R.id.note_edit_view);
                int index = e.getSelectionStart(); //获取光标所在位置
                Log.d(TAG, "Index是: " + index);
                Editable edit_text = e.getEditableText();
                edit_text.insert(index, spannableString); //将图片插入到光标所在位置

                mWorkingNote.mContent = e.getText().toString();
                //6.把改动提交到数据库中,两个数据库表都要改的
                ContentResolver contentResolver = getContentResolver();
                ContentValues contentValues = new ContentValues();
                final long id = mWorkingNote.getNoteId();
                contentValues.put("snippet", mWorkingNote.mContent);
                contentResolver.update(Uri.parse("content://micode_notes/note"), contentValues, "_id=?", new String[]{"" + id});
                ContentValues contentValues1 = new ContentValues();
                contentValues1.put("content", mWorkingNote.mContent);
                contentResolver.update(Uri.parse("content://micode_notes/data"), contentValues1, "mime_type=? and note_id=?", new String[]{"vnd.android.cursor.item/text_note", "" + id});

            } else {
                Toast.makeText(NoteEditActivity.this, "获取图片失败", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showToast(int resId) {
        showToast(resId, Toast.LENGTH_SHORT);
@@ -907,5 +988,121 @@ private void showToast(int resId, int duration) {
    public void OnOpenMenu(View view) {
		openOptionsMenu();
	}

    //0406新增
    //路径字符串格式 转换为 图片image格式
    private void convertToImage() {
        NoteEditText noteEditText = (NoteEditText) findViewById(R.id.note_edit_view); //获取当前的edit
        Editable editable = noteEditText.getText();//1.获取text
        String noteText = editable.toString(); //2.将note内容转换为字符串
        int length = editable.length(); //内容的长度
        //3.截取img片段 [local]+uri+[local]，提取uri
        for(int i = 0; i < length; i++) {
            for(int j = i; j < length; j++) {
                String img_fragment = noteText.substring(i, j+1); //img_fragment：关于图片路径的片段
                if(img_fragment.length() > 15 && img_fragment.endsWith("[/local]") && img_fragment.startsWith("[local]")){
                    int limit = 7;  //[local]为7个字符
                    //[local][/local]共15个字符，剩下的为真正的path长度
                    int len = img_fragment.length()-15;
                    //从[local]之后的len个字符就是path
                    String path = img_fragment.substring(limit,limit+len);//获取到了图片路径
                    Bitmap bitmap = null;
                    Log.d(TAG, "图片的路径是："+path);
                    try {
                        bitmap = BitmapFactory.decodeFile(path);//将图片路径解码为图片格式
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if(bitmap!=null){  //若图片存在
                        Log.d(TAG, "图片不为null");
                        ImageSpan imageSpan = new ImageSpan(NoteEditActivity.this, bitmap);
                        //4.创建一个SpannableString对象，以便插入用ImageSpan对象封装的图像
                        String ss = "[local]" + path + "[/local]";
                        SpannableString spannableString = new SpannableString(ss);
                        //5.将指定的标记对象附加到文本的开始...结束范围
                        spannableString.setSpan(imageSpan, 0, ss.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        Log.d(TAG, "Create spannable string success!");
                        Editable edit_text = noteEditText.getEditableText();
                        edit_text.delete(i,i+len+15); //6.删掉图片路径的文字
                        edit_text.insert(i, spannableString); //7.在路径的起始位置插入图片
                    }
                }
            }
        }
    }
    //0406新增
    //获取文件的real path
    public String getPath(final Context context, final Uri uri) {

        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
//            if (isExternalStorageDocument(uri)) {
//                final String docId = DocumentsContract.getDocumentId(uri);
//                final String[] split = docId.split(":");
//                final String type = split[0];
//
//                if ("primary".equalsIgnoreCase(type)) {
//                    return Environment.getExternalStorageDirectory() + "/" + split[1];
//                }
//            }
//            // DownloadsProvider
//            else if (isDownloadsDocument(uri)) {
//                final String id = DocumentsContract.getDocumentId(uri);
//                final Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
//                return getDataColumn(context, contentUri, null, null);
//            }
            // MediaProvider
//            else
            if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[]{split[1]};

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        }
        // Media
        else if ("content".equalsIgnoreCase(uri.getScheme())) {
            return getDataColumn(context, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        return null;
    }

    public boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }
    public String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {

        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {column};

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }
}
