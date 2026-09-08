# Sango 山河底圖

這兩張 JPEG 為 Sango 本次製作的程序式原創示意素材，未使用既有三國遊戲截圖或第三方地圖底圖。

- `china_42_terrain.jpg`：全國節點圖。
- `central_region_terrain.jpg`：舊六城區域圖，供既有存檔使用。

均為 2040 × 1360 RGB JPEG；沒有城市文字、道路、按鈕或 UI。地形只作視覺背景，不作歷史考據或行軍限制。

程式透過 maps.json 的 `backgroundAssetPath` 取得資產，由 `MapTerrainBackground` 擁有 Texture；`StrategicMapWidget` 在裁切後、道路和城池之前繪製，不參與命中測試。

製作採固定亂數種子的紙張紋理、簡化地形與筆描山峰／河流；遊戲執行時直接讀取 JPEG，不需 Python、影像生成服務或網路。
