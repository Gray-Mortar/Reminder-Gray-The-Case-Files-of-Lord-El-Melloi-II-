package com.example.eisenhowertodo;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import java.util.List;

public class QuickAddWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        List<Task> tasks = new TaskStore(context).all();
        int[] counts = new int[4];
        Task[] first = new Task[4];
        for (Task task : tasks) {
            int quadrant = Math.max(0, Math.min(3, task.quadrant));
            counts[quadrant]++;
            if (first[quadrant] == null || task.order < first[quadrant].order) {
                first[quadrant] = task;
            }
        }
        int[] quadrants = {R.id.widget_q0, R.id.widget_q1, R.id.widget_q2, R.id.widget_q3};
        int[] countIds = {R.id.widget_count0, R.id.widget_count1,
                R.id.widget_count2, R.id.widget_count3};
        int[] taskIds = {R.id.widget_task0, R.id.widget_task1,
                R.id.widget_task2, R.id.widget_task3};
        for (int appWidgetId : appWidgetIds) {
            Intent input = new Intent(context, MainActivity.class);
            input.putExtra(MainActivity.EXTRA_FOCUS_INPUT, true);
            input.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP |
                    Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent inputIntent = PendingIntent.getActivity(context, appWidgetId * 10,
                    input,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_matrix);
            views.setOnClickPendingIntent(R.id.widget_input, inputIntent);
            for (int quadrant = 0; quadrant < 4; quadrant++) {
                Intent detail = new Intent(context, QuadrantDetailActivity.class);
                detail.putExtra(QuadrantDetailActivity.EXTRA_QUADRANT, quadrant);
                detail.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                PendingIntent detailIntent = PendingIntent.getActivity(context,
                        appWidgetId * 10 + quadrant + 1, detail,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                views.setOnClickPendingIntent(quadrants[quadrant], detailIntent);
                views.setTextViewText(countIds[quadrant], String.valueOf(counts[quadrant]));
                views.setTextViewText(taskIds[quadrant],
                        first[quadrant] == null ? "" : first[quadrant].text);
            }
            manager.updateAppWidget(appWidgetId, views);
        }
    }

    static void refresh(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(
                new ComponentName(context, QuickAddWidgetProvider.class));
        if (ids.length > 0) new QuickAddWidgetProvider().onUpdate(context, manager, ids);
    }
}
