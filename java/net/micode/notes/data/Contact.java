/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
//联系人数据库
package net.micode.notes.data;
// 定义数据包名

// 引入必要的Android类库和Java类库
import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Data;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import java.util.HashMap;

// 定义一个公共类Contact来处理联系人相关操作
public class Contact {
    // 定义一个静态HashMap用于缓存联系人的姓名和电话，以电话号码为键，姓名为值
    private static HashMap<String, String> sContactCache;

    // 定义一个日志标签
    private static final String TAG = "Contact";

    // 定义查询的语句，用于在联系人数据库中查找电话号码，并限制搜索范围
    private static final String CALLER_ID_SELECTION = "PHONE_NUMBERS_EQUAL(" + Phone.NUMBER
    + ",?) AND " + Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "'"
    + " AND " + Data.RAW_CONTACT_ID + " IN "
            + "(SELECT raw_contact_id "
            + " FROM phone_lookup"
            + " WHERE min_match = '+')";

    // 定义一个静态方法用来通过电话号码获取联系人姓名
    public static String getContact(Context context, String phoneNumber) {
        // 首次调用时初始化HashMap
        if(sContactCache == null) {
            sContactCache = new HashMap<String, String>();
        }

        // 如果缓存中已有此电话号码对应的联系人，直接返回联系人姓名
        if(sContactCache.containsKey(phoneNumber)) {
            return sContactCache.get(phoneNumber);
        }
        // 替换查询语句中的+号，以匹配电话号码的格式
        String selection = CALLER_ID_SELECTION.replace("+",
                PhoneNumberUtils.toCallerIDMinMatch(phoneNumber));

        // 根据替换后的查询语句，从联系人数据库中查询符合条件的联系人
        Cursor cursor = context.getContentResolver().query(
                Data.CONTENT_URI,
                new String [] { Phone.DISPLAY_NAME },
                selection,
                new String[] { phoneNumber },
                null);

        // 如果查询结果不为空，且有至少一个结果
        if (cursor != null && cursor.moveToFirst()) {
            try {
                // 获取第一条记录的姓名字段
                String name = cursor.getString(0);
                // 将电话号码和姓名存入缓存
                sContactCache.put(phoneNumber, name);
                // 返回联系人姓名
                return name;
            } catch (IndexOutOfBoundsException e) {
                // 查询过程中出现异常，记录日志并返回null
                Log.e(TAG, " Cursor get string error " + e.toString());
                return null;
            } finally {
                // 最终确保关闭cursor，释放资源
                cursor.close();
            }
        } else {
            // 如果未查询到结果，记录日志并返回null
            Log.d(TAG, "No contact matched with number:" + phoneNumber);
            return null;
        }
    }
}
//这段代码为Android开发中一个通用的模式，旨在提供一个通过电话号码获取联系人姓名的功能。此外，使用了缓存机制来提升查询效率，减少对数据库的直接访问，从而优化性能。