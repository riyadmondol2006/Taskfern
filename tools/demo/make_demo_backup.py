#!/usr/bin/env python3
"""
Writes a Taskfern backup file filled with fictional demonstration data, with dates relative to a given day.

It is used to take README screenshots with realistic content: import it on a test device with
Settings → Backup & restore → Import → Replace. It is never bundled into the app, so no user ever gets demo tasks.

usage: python3 -I tools/demo/make_demo_backup.py [YYYY-MM-DD] > taskfern-demo.json
"""
import datetime as dt
import json
import sys

today = dt.date.fromisoformat(sys.argv[1]) if len(sys.argv) > 1 else dt.date.today()
tz = dt.datetime.now().astimezone().tzinfo


def ms(day_offset=0, hour=9, minute=0):
    d = today + dt.timedelta(days=day_offset)
    return int(dt.datetime(d.year, d.month, d.day, hour, minute, tzinfo=tz).timestamp() * 1000)


def day(offset):
    return (today + dt.timedelta(days=offset)).isoformat()


WEEKDAYS = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"]

lists = [
    {"id": 1, "name": "Work", "color": 1, "position": 0, "favorite": True, "createdAt": ms(-30)},
    {"id": 2, "name": "Home", "color": 0, "position": 1, "createdAt": ms(-30)},
    {"id": 3, "name": "Errands", "color": 3, "position": 2, "createdAt": ms(-30)},
    {"id": 4, "name": "Health", "color": 4, "position": 3, "createdAt": ms(-30)},
]

tasks = []
subtasks = []
reminders = []
next_id = {"task": 1, "sub": 1, "rem": 1}


occurrence_log = []


def task(title, list_id=None, due=None, time=None, priority=0, notes="", tags=(), rec=None, rems=(), subs=(),
         completed_at=None, pinned=False, created=-7, history=(), scope=None, due_iso=None):
    tid = next_id["task"]
    next_id["task"] += 1
    t = {
        "id": tid, "listId": list_id, "title": title, "notes": notes, "priority": priority,
        "dueDate": due_iso or (day(due) if due is not None else None), "dueTime": time, "tags": list(tags), "pinned": pinned,
        "completed": completed_at is not None, "completedAt": completed_at,
        "createdAt": ms(created, 8), "updatedAt": ms(created, 8), "position": tid,
    }
    if scope:
        t["dueScope"] = scope
    if rec:
        t["recurrence"] = rec
        # The series started at its first logged occurrence, so a history of on-time completions forms a streak.
        t["seriesAnchor"] = day(min(list(history) + [due if due is not None else 0]))
        t["occurrenceCount"] = len(history)
    for offset in history:
        occurrence_log.append({"id": len(occurrence_log) + 1, "taskId": tid, "occurrenceDate": day(offset),
                               "action": "completed", "at": ms(offset, 19)})
    tasks.append(t)
    for offset in rems:
        reminders.append({"id": next_id["rem"], "taskId": tid, "offsetMinutes": offset})
        next_id["rem"] += 1
    for i, (s, done) in enumerate(subs):
        subtasks.append({"id": next_id["sub"], "taskId": tid, "title": s, "done": done, "position": i})
        next_id["sub"] += 1
    return tid


# Overdue and today
task("Renew car insurance", 3, due=-1, priority=3, rems=[0], notes="Compare the two quotes from last week first.")
task("Prepare quarterly report", 1, due=0, time="14:00", priority=3, tags=["q4"], rems=[30],
     subs=[("Collect sales figures", True), ("Draft summary", False), ("Send to Priya for review", False)])
task("Team stand-up notes", 1, due=0, time="09:30", rems=[0])
task("Pick up dry cleaning", 3, due=0, priority=1)
task("Call Mum", 2, due=0, time="18:30", rems=[0],
     rec={"frequency": "WEEKLY", "interval": 1, "weekdays": [WEEKDAYS[today.weekday()]]},
     history=[-42, -35, -28, -21, -14, -7])
task("Take vitamins", 4, due=0, time="08:00", rec={"frequency": "DAILY", "interval": 1},
     history=list(range(-9, 0)))
task("Water the plants", 2, due=0, rec={"frequency": "DAILY", "interval": 3, "fromCompletion": True})
# Upcoming
task("Dentist appointment", 4, due=1, time="10:00", priority=2, rems=[60, 1440], notes="Dr. Okafor, 2nd floor.")
task("Book flights to Lisbon", None, due=1, tags=["travel"])
task("Yoga class", 4, due=2, time="18:00",
     rec={"frequency": "WEEKLY", "interval": 1, "weekdays": [WEEKDAYS[(today.weekday() + 2) % 7], WEEKDAYS[(today.weekday() + 5) % 7]]})
task("Pay electricity bill", 2, due=4, priority=2, rems=[0], rec={"frequency": "MONTHLY", "interval": 1})
task("Plan weekend hike", None, due=3, tags=["outdoors"],
     subs=[("Check the weather", False), ("Pack snacks", False)])
task("Submit expense report", 1, due=6, priority=1)
task("Book car service", 3, due=9)
# Plans: no fixed day, just this week / month / year
week_end = today + dt.timedelta(days=6 - today.weekday())
month_end = (today.replace(day=28) + dt.timedelta(days=4)).replace(day=1) - dt.timedelta(days=1)
task("Clean out the garage", 2, scope="week", due_iso=week_end.isoformat(),
     subs=[("Sort boxes", True), ("Donate old bikes", False), ("Sweep the floor", False)])
task("Book a summer holiday", None, scope="month", due_iso=month_end.isoformat(), tags=["travel"])
task("Run a 10k", 4, scope="year", due_iso=today.replace(month=12, day=31).isoformat(), priority=2)
# No date
task("Read \"The Overstory\"", None, priority=1, tags=["reading"])
task("Fix the squeaky door", 2)
task("Sort out old photos", 2, created=-2)
# Completed today
task("Morning run", 4, due=0, completed_at=ms(0, 7, 10))
task("Reply to Anna's email", 1, due=0, completed_at=ms(0, 8, 40))

shopping_lists = [
    {"id": 1, "name": "Weekly groceries", "color": 0, "position": 0, "createdAt": ms(-10)},
    {"id": 2, "name": "Hardware store", "color": 2, "position": 1, "createdAt": ms(-5)},
]
items = []


def item(list_id, name, cat, qty="", unit="", checked=False, price=None):
    iid = len(items) + 1
    items.append({
        "id": iid, "listId": list_id, "name": name, "quantity": qty, "unit": unit, "category": cat,
        "checked": checked, "position": iid, "priceCents": price, "createdAt": ms(-1), "checkedAt": ms(0, 10) if checked else None,
    })


for args in [
    (1, "Bananas", "Produce", "6", "", False, 30), (1, "Spinach", "Produce", "1", "bag", False, 199),
    (1, "Tomatoes", "Produce", "500", "g", True, 249), (1, "Avocados", "Produce", "2", "", False, 150),
    (1, "Sourdough bread", "Bakery", "1", "loaf", False, 395), (1, "Oat milk", "Dairy & eggs", "2", "l", False, 179),
    (1, "Eggs", "Dairy & eggs", "1", "dozen", True, 329), (1, "Greek yogurt", "Dairy & eggs", "1", "", False, 249),
    (1, "Salmon fillets", "Meat & fish", "2", "", False, 899), (1, "Basmati rice", "Pantry", "1", "kg", False, 289),
    (1, "Olive oil", "Pantry", "1", "bottle", False, 749), (1, "Coffee beans", "Pantry", "250", "g", False, 699),
    (1, "Dish soap", "Household", "1", "", False, 249),
    (2, "Wood screws", "Other", "1", "box"), (2, "Picture hooks", "Other", "4"), (2, "Sandpaper", "Other", "2"),
]:
    item(*args)

memory = [
    {"nameKey": n.lower(), "name": n, "category": c, "useCount": u, "lastUsedAt": ms(-u)}
    for n, c, u in [("Milk", "Dairy & eggs", 9), ("Bananas", "Produce", 8), ("Bread", "Bakery", 7), ("Eggs", "Dairy & eggs", 6),
                    ("Coffee beans", "Pantry", 5), ("Apples", "Produce", 4), ("Cheese", "Dairy & eggs", 3), ("Pasta", "Pantry", 3)]
]

routines = [
    {"id": 1, "name": "Morning routine", "emoji": "☀️", "color": 0, "weekdays": WEEKDAYS, "time": "07:00", "position": 0, "createdAt": ms(-30)},
    {"id": 2, "name": "Evening wind-down", "emoji": "🌙", "color": 2, "weekdays": WEEKDAYS, "time": "21:30", "position": 1, "createdAt": ms(-30)},
    {"id": 3, "name": "Weekly reset", "emoji": "🧹", "color": 3, "weekdays": ["SUNDAY"], "position": 2, "createdAt": ms(-30)},
]
steps = []
for rid, names in [
    (1, ["Drink a glass of water", "Stretch for 5 minutes", "Make the bed", "Review today's tasks"]),
    (2, ["Tidy up for 10 minutes", "Prepare tomorrow", "Read 20 pages", "Screens off"]),
    (3, ["Plan the week", "Laundry", "Clean the fridge"]),
]:
    for i, n in enumerate(names):
        steps.append({"id": len(steps) + 1, "routineId": rid, "title": n, "position": i})

checks = []
# Morning routine fully done on the last 12 days and half done today; evening done most days.
for back in range(1, 13):
    for s in steps:
        if s["routineId"] == 1 or (s["routineId"] == 2 and back % 4 != 0):
            checks.append({"stepId": s["id"], "routineId": s["routineId"], "date": day(-back), "checkedAt": ms(-back, 7)})
for s in steps[:2]:
    checks.append({"stepId": s["id"], "routineId": 1, "date": day(0), "checkedAt": ms(0, 7, 5)})

templates = [
    {"id": 1, "kind": "shopping", "name": "Weekly groceries", "emoji": "🛒", "builtin": True, "position": 0,
     "items": ["Milk", "Eggs", "Bread", "Bananas", "Apples", "Tomatoes", "Onions", "Rice", "Cheese", "Coffee"]},
    {"id": 2, "kind": "tasks", "name": "Packing", "emoji": "🧳", "builtin": True, "position": 1,
     "items": ["Passport / ID", "Phone charger", "Toothbrush & toothpaste", "Medication", "Clothes for each day",
               "Sleepwear", "Rain jacket", "Headphones", "Travel documents", "Snacks for the journey"]},
    {"id": 3, "kind": "tasks", "name": "House cleaning", "emoji": "🧽", "builtin": True, "position": 2,
     "items": ["Dust surfaces", "Vacuum floors", "Mop kitchen and bathroom", "Clean bathroom sink and mirror",
               "Change bed sheets", "Take out recycling", "Wipe kitchen counters", "Water plants"]},
    {"id": 4, "kind": "tasks", "name": "Weekly planning", "emoji": "🗓️", "builtin": True, "position": 3,
     "items": ["Review last week", "Check calendar for the coming week", "Pick three priorities", "Plan meals",
               "Schedule exercise", "Clear the inbox list"]},
    {"id": 5, "kind": "tasks", "name": "Study schedule", "emoji": "📚", "builtin": True, "position": 4,
     "items": ["Review lecture notes", "Read the next chapter", "Do practice problems", "Make flashcards",
               "Revise flashcards", "Prepare questions for class"]},
    {"id": 6, "kind": "routine", "name": "Morning routine", "emoji": "☀️", "builtin": True, "position": 5,
     "items": ["Drink a glass of water", "Stretch for 5 minutes", "Make the bed", "Review today's tasks"]},
    {"id": 7, "kind": "routine", "name": "Evening wind-down", "emoji": "🌙", "builtin": True, "position": 6,
     "items": ["Tidy up for 10 minutes", "Prepare tomorrow", "Read", "Screens off"]},
]

focus = [
    {"id": i + 1, "taskId": 2, "startedAt": ms(-i, 10), "plannedMinutes": 25, "focusedSeconds": 1500, "completed": True}
    for i in range(4)
]

backup = {
    "format": "taskfern-backup", "version": 1, "exportedAt": ms(0, 6), "appVersion": "demo",
    "settings": {"userName": "Alex", "showShoppingPrices": True, "currencySymbol": "€"},
    "taskLists": lists, "tasks": tasks, "subtasks": subtasks, "reminders": reminders, "occurrenceLog": occurrence_log,
    "shoppingLists": shopping_lists, "shoppingItems": items, "itemMemory": memory,
    "routines": routines, "routineSteps": steps, "routineChecks": checks, "templates": templates,
    "focusSessions": focus,
}
json.dump(backup, sys.stdout, ensure_ascii=False, indent=1)
