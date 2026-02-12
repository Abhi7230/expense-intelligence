# ── Expense Intelligence ProGuard Rules ──

# Keep Room entities (they use reflection for database mapping)
-keep class com.example.myapplication.NotificationEntity { *; }
-keep class com.example.myapplication.AppUsageEntity { *; }

# Keep DAOs (Room generates implementations at compile time)
-keep interface com.example.myapplication.NotificationDao { *; }
-keep interface com.example.myapplication.AppUsageDao { *; }

# Keep data classes used for JSON parsing (AI responses)
-keep class com.example.myapplication.AiInsightEngine$AiInsight { *; }
-keep class com.example.myapplication.InsightGenerator$* { *; }
-keep class com.example.myapplication.CorrelationEngine$* { *; }
-keep class com.example.myapplication.TransactionParser$* { *; }
-keep class com.example.myapplication.AppKnowledgeBase$* { *; }

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep NotificationListenerService (bound by the OS via manifest)
-keep class com.example.myapplication.MyNotificationListenerService { *; }
-keep class com.example.myapplication.MyForegroundService { *; }
