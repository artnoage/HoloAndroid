plugins {
    id("com.android.asset-pack")
}

assetPack {
    packName = "ml_models"
    dynamicDelivery {
        deliveryType = "on-demand"
    }
}