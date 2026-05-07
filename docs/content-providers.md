# Content Providers

## Confirmed Working

### CarStatusProvider

- **Package**: `com.byd.providers.carstatus`
- **Authority**: `com.byd.carStatusProvider`
- **URI**: `content://com.byd.carStatusProvider/car_status`
- **Access**: Works from ADB shell without special permissions

#### Schema

| Key | Type | Description |
|-----|------|-------------|
| `travel_points_fuel` | string | Fuel consumption per trip segment (# delimited) |
| `travel_points_elec` | string | Electric consumption per trip segment (Wh, # delimited, 1347=separator) |
| `travel_points_fuel_one` | string | Trip 1 fuel data |
| `travel_points_elec_one` | string | Trip 1 electric data |
| `travel_points_fuel_two` | string | Trip 2 fuel data |
| `travel_points_elec_two` | string | Trip 2 electric data |
| `car_status_issue` | string | Active vehicle issues |
| `car_status_issue_num` | int | Number of active issues |
| `car_status_maintenance_time` | int | Days until next maintenance |
| `car_status_maintenance_mile` | int | Km until next maintenance |
| `set_car_status_maintenance_time` | int | Maintenance interval (days) |
| `set_car_status_maintenance_mile` | int | Maintenance interval (km) |
| `car_status_maintenance_hev_mile` | int | HEV-specific maintenance km |
| `dicare_engine_oil_no_prompt` | bool | Engine oil prompt suppressed |
| `dicare_at_fluid_no_prompt` | bool | AT fluid prompt suppressed |
| `dicare_brake_fluid_no_prompt` | bool | Brake fluid prompt suppressed |
| `dicare_battery_coolant_no_prompt` | bool | Battery coolant prompt suppressed |
| `dicare_motor_coolant_no_prompt` | bool | Motor coolant prompt suppressed |
| `maintenance_time_switch_state` | bool | Time-based maintenance enabled |
| `maintenance_mile_switch_state` | bool | Mileage-based maintenance enabled |
| `tyre_pressure_guidance_no_prompt` | bool | Tyre pressure prompt suppressed |

#### Query Example

```bash
adb shell "content query --uri content://com.byd.carStatusProvider/car_status"
```

#### Electric Consumption Data Format

The `travel_points_elec` field contains `#`-delimited values representing energy consumption (likely in Wh/10) per distance segment. The value `1347` appears to be a trip separator.

Example: `199#162#140#1347#118#113#456#...`

This means: segment1=199, segment2=162, segment3=140, [new trip], segment1=118, ...

## Partially Working

### CarServiceProvider

- **Package**: `com.byd.car.server`
- **Authority**: `com.byd.car.server.provider.CarServiceProvider`
- **URI**: `content://com.byd.car.server.provider.CarServiceProvider/`
- **Access**: Returns empty results (may need specific paths or system-level access)

### VehicleServiceProvider (GPack)

- **Package**: `com.byd.car.server` (GPack component)
- **Authority**: `com.gpack.service.provider.VehicleServiceProvider`
- **URI**: `content://com.gpack.service.provider.VehicleServiceProvider/`

### VehicleContentProvider (VEM)

- **Package**: `com.byd.car.server`
- **Authorities**: `com_byd_vem_data`, `com_byd_map_ui`, `google_maps_assisted_driving`, `google_maps_energy`, `google_maps_settings`, `google_maps_vehicle_profile`
- **Access**: Returns "Query unsupported" — likely uses `call()` method instead of `query()`

## Not Yet Explored

### CarSettingsProvider

- **Package**: `com.byd.providers.carsettings`
- **Authority**: `com.byd.providers.carsettings`
- **Handlers**: ConfigHandler, GlobalHandler, SystemSettingHandler, UserTableDataHandler, TravelInfoHandler, DiCareRecordHandler

### BluetoothRemoteProvider

- **Package**: `com.byd.bluetoothprovider`

### AppOpsProvider

- **Package**: `com.byd.providers.appops`

## Android Settings with Car Data

Queryable via `settings list system`:

| Key | Example Value | Description |
|-----|--------------|-------------|
| `KEY_VEHICLE_TYPE` | 127 | Vehicle model ID |
| `KEY_DEVICE_ID` | [YOUR_DEVICE_ID] | Head unit device ID |
| `KEY_REALLY_CARMODE` | car_7F_0_0_4 | Current car mode |
| `EXIST_CAR_SETTINGS` | 1 | Car settings available |
| `EXIST_CAR_WINDOW` | 1 | Window control available |
| `EXIST_CAR_DORMANT_WINDOW` | 0 | Dormant window feature |
| `qs_panel_new_vehicle_sel_items` | simulator,data,... | Active quick panel tiles |
| `qs_panel_new_vehicle_un_sel_items` | rotationlock,... | Available but hidden tiles |
| `charging_sounds_enabled` | 1 | Charging connection sound |
| `lockscreen_sounds_enabled` | 0 | Lock screen sounds |
