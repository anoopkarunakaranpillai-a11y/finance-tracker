package com.anoop.financetracker.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.anoop.financetracker.FinanceApp
import com.anoop.financetracker.MainActivity
import com.anoop.financetracker.R
import com.anoop.financetracker.data.AppSettings
import com.anoop.financetracker.data.BillEntity
import com.anoop.financetracker.data.BudgetEntity
import com.anoop.financetracker.data.GoalEntity
import com.anoop.financetracker.data.TxEntity
import com.anoop.financetracker.domain.Categories
import com.anoop.financetracker.domain.DateRange
import com.anoop.financetracker.domain.FinanceMath
import com.anoop.financetracker.domain.Money
import com.anoop.financetracker.domain.parseDate
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/** A notification the app wants to show, with a key so it is only shown once. */
data class Alert(val key: String, val channel: String, val title: String, val text: String)

/** Pure decision logic for reminders, kept separate from Android so it can be unit-tested. */
object AlertPlanner {
    fun plan(
        s: AppSettings, today: LocalDate, bills: List<BillEntity>, budgets: List<BudgetEntity>,
        txs: List<TxEntity>, goals: List<GoalEntity>, postedRecurring: List<TxEntity>,
    ): List<Alert> {
        val out = ArrayList<Alert>()
        val cur = s.currency
        if (s.notifyBills) for (b in bills) {
            if (b.done) continue
            val due = parseDate(b.due) ?: continue
            val days = ChronoUnit.DAYS.between(today, due)
            if (days < 0) out += Alert("bill-over-${b.id}-${b.due}-$today", Notifier.CH_BILLS, "Overdue: ${b.title}", "${Money.format(b.amountMils, cur)} was due on ${b.due}.")
            else if (days <= b.remindDays) out += Alert("bill-${b.id}-${b.due}", Notifier.CH_BILLS,
                if (days == 0L) "Due today: ${b.title}" else "${b.title} due in $days day${if (days == 1L) "" else "s"}",
                "${Money.format(b.amountMils, cur)}${if (b.provider.isNotBlank()) " · " + b.provider else ""}")
        }
        if (s.notifyBudget) {
            val ym = YearMonth.from(today)
            val st = FinanceMath.budgetStatus(budgets, txs, DateRange(ym.atDay(1), ym.atEndOfMonth()))
            for (l in st.lines) {
                val name = Categories.find(l.category).name
                if (l.level == 2) out += Alert("bud100-${l.category}-$ym", Notifier.CH_BUDGET, "Budget used up: $name", "${Money.format(l.spentMils, cur)} spent of ${Money.format(l.limitMils, cur)} this month.")
                else if (l.level == 1) out += Alert("bud80-${l.category}-$ym", Notifier.CH_BUDGET, "80% of $name budget used", "${Money.format(l.remainingMils, cur)} left for this month.")
            }
        }
        if (s.notifyGoals) for (g in goals) {
            val pct = (FinanceMath.goalRatio(g) * 100).toInt()
            val m = listOf(100, 75, 50, 25).firstOrNull { pct >= it } ?: continue
            out += Alert("goal-${g.id}-$m", Notifier.CH_GOALS, if (m == 100) "Goal reached: ${g.name} 🎉" else "${g.name}: $m% saved",
                "${Money.format(g.savedMils, cur)} of ${Money.format(g.targetMils, cur)}")
        }
        if (s.notifyRecurring && postedRecurring.isNotEmpty()) {
            val total = postedRecurring.size
            out += Alert("rec-${today}-$total", Notifier.CH_RECURRING, "Recorded $total recurring transaction${if (total == 1) "" else "s"}",
                postedRecurring.take(3).joinToString(" · ") { (it.note.ifBlank { Categories.find(it.category).name }) + " " + Money.format(it.amountMils, cur) })
        }
        if (s.notifySummary && today.dayOfMonth == 1) {
            val last = YearMonth.from(today).minusMonths(1)
            val sm = FinanceMath.summary(txs, DateRange(last.atDay(1), last.atEndOfMonth()))
            out += Alert("sum-$last", Notifier.CH_SUMMARY, "Your ${last.month.name.lowercase().replaceFirstChar { it.uppercase() }} summary",
                "Income ${Money.format(sm.incomeMils, cur)} · Spent ${Money.format(sm.expenseMils, cur)} · Saved ${Money.format(sm.netMils, cur, true)}")
        }
        return out
    }
}

object Notifier {
    const val CH_BILLS = "bills"
    const val CH_BUDGET = "budget"
    const val CH_GOALS = "goals"
    const val CH_RECURRING = "recurring"
    const val CH_SUMMARY = "summary"

    fun createChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        listOf(
            Triple(CH_BILLS, "Upcoming bills", NotificationManager.IMPORTANCE_HIGH),
            Triple(CH_BUDGET, "Budget limits", NotificationManager.IMPORTANCE_DEFAULT),
            Triple(CH_GOALS, "Savings milestones", NotificationManager.IMPORTANCE_DEFAULT),
            Triple(CH_RECURRING, "Recurring transactions", NotificationManager.IMPORTANCE_LOW),
            Triple(CH_SUMMARY, "Monthly summary", NotificationManager.IMPORTANCE_DEFAULT),
        ).forEach { (id, name, imp) -> nm.createNotificationChannel(NotificationChannel(id, name, imp)) }
    }

    fun canPost(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(ctx: Context, a: Alert) {
        if (!canPost(ctx)) return
        val pi = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, a.channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(a.title)
            .setContentText(a.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(a.text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            // Amounts are hidden on the lock screen.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(NotificationCompat.Builder(ctx, a.channel).setSmallIcon(R.drawable.ic_notification).setContentTitle("Anoop's Finance reminder").build())
            .build()
        try { NotificationManagerCompat.from(ctx).notify(a.key.hashCode(), n) } catch (e: SecurityException) { /* permission revoked */ }
    }
}

/** Runs once a day around 9:00: posts due recurring transactions, then shows any reminders that are switched on. */
class DailyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? FinanceApp ?: return Result.success()
        val c = app.container() ?: return Result.retry()
        return try {
            val posted = c.repo.postDueRecurring()
            val s = c.settings.current()
            if (s.anyNotifications) {
                val today = LocalDate.now()
                val snap = c.repo.snapshot(s.currency)
                val alerts = AlertPlanner.plan(s, today, snap.bills, snap.budgets, snap.txs, snap.goals, posted)
                val fired = c.settings.firedKeys()
                val fresh = alerts.filter { it.key !in fired }
                fresh.forEach { Notifier.show(applicationContext, it) }
                c.settings.addFired(fresh.map { it.key }.toSet())
            }
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val NAME = "daily-finance"

        fun schedule(ctx: Context) {
            val now = LocalDateTime.now()
            var at = now.toLocalDate().atTime(9, 0)
            if (!at.isAfter(now)) at = at.plusDays(1)
            val delay = Duration.between(now, at).toMinutes()
            val req = PeriodicWorkRequestBuilder<DailyWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
