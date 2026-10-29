package com.riyadmondol2006.taskfern.domain

/**
 * Offline guess of a shopping item's aisle from its name, so new items land in a sensible group without extra taps.
 * English keywords only; anything unknown goes to [OTHER] and the user can change it (the choice is remembered).
 */
object GroceryCategories {
    const val PRODUCE = "Produce"
    const val DAIRY = "Dairy & eggs"
    const val BAKERY = "Bakery"
    const val MEAT = "Meat & fish"
    const val PANTRY = "Pantry"
    const val FROZEN = "Frozen"
    const val DRINKS = "Drinks"
    const val SNACKS = "Snacks"
    const val HOUSEHOLD = "Household"
    const val PERSONAL = "Personal care"
    const val BABY_PET = "Baby & pet"
    const val OTHER = "Other"

    /** Display order of the aisles in a list. */
    val ordered = listOf(PRODUCE, BAKERY, DAIRY, MEAT, PANTRY, FROZEN, SNACKS, DRINKS, HOUSEHOLD, PERSONAL, BABY_PET, OTHER)

    private val keywords: Map<String, List<String>> = mapOf(
        PRODUCE to listOf(
            "apple", "banana", "orange", "lemon", "lime", "grape", "berry", "berries", "strawberr", "blueberr", "melon",
            "mango", "pear", "peach", "plum", "cherr", "kiwi", "pineapple", "avocado", "tomato", "potato", "onion",
            "garlic", "ginger", "carrot", "lettuce", "spinach", "kale", "cabbage", "broccoli", "cauliflower", "pepper",
            "cucumber", "zucchini", "courgette", "eggplant", "aubergine", "mushroom", "celery", "herb", "basil",
            "parsley", "coriander", "cilantro", "mint", "salad", "fruit", "vegetable", "veg", "corn", "pea", "bean sprout",
        ),
        DAIRY to listOf("milk", "cheese", "butter", "yogurt", "yoghurt", "cream", "egg", "curd", "ghee", "kefir", "paneer"),
        BAKERY to listOf("bread", "bagel", "bun", "roll", "croissant", "baguette", "muffin", "cake", "tortilla", "pita", "naan"),
        MEAT to listOf(
            "chicken", "beef", "pork", "lamb", "mutton", "turkey", "bacon", "ham", "sausage", "mince", "steak", "fish",
            "salmon", "tuna", "shrimp", "prawn", "cod", "meat",
        ),
        PANTRY to listOf(
            "rice", "pasta", "noodle", "flour", "sugar", "salt", "oil", "vinegar", "sauce", "ketchup", "mayo", "mustard",
            "honey", "jam", "cereal", "oat", "lentil", "dal", "bean", "chickpea", "spice", "cumin", "turmeric", "stock",
            "soup", "can ", "canned", "tin", "peanut butter", "baking", "yeast", "coffee", "tea", "cocoa",
        ),
        FROZEN to listOf("frozen", "ice cream", "ice"),
        DRINKS to listOf("water", "juice", "soda", "cola", "beer", "wine", "drink", "lemonade", "sparkling"),
        SNACKS to listOf("chips", "crisps", "chocolate", "biscuit", "cookie", "cracker", "nuts", "popcorn", "candy", "snack"),
        HOUSEHOLD to listOf(
            "detergent", "soap", "dish", "sponge", "paper towel", "kitchen roll", "tissue", "toilet paper", "trash bag",
            "bin bag", "foil", "cling", "bleach", "cleaner", "battery", "batteries", "bulb", "laundry",
        ),
        PERSONAL to listOf(
            "shampoo", "conditioner", "toothpaste", "toothbrush", "deodorant", "razor", "lotion", "sunscreen", "floss",
            "body wash", "medicine", "vitamin", "plaster",
        ),
        BABY_PET to listOf("diaper", "nappy", "wipes", "baby", "formula", "dog", "cat", "pet", "litter"),
    )

    fun guess(name: String): String {
        val n = " " + name.lowercase().trim() + " "
        // Longer keywords first so "peanut butter" beats "butter" and "ice cream" beats "cream".
        val all = keywords.flatMap { (cat, words) -> words.map { it to cat } }.sortedByDescending { it.first.length }
        return all.firstOrNull { (word, _) -> n.contains(word) }?.second ?: OTHER
    }
}
