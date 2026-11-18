package com.buct.course;

import androidx.appcompat.app.AppCompatActivity;

public class CourseDetailActivity extends AppCompatActivity {
    @Override
    protected void attachBaseContext(android.content.Context context) {
        super.attachBaseContext(com.buct.course.manager.AppThemeCoordinator.INSTANCE.wrapContext(context));
    }
}
