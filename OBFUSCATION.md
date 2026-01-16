# Code Obfuscation Guide

This project uses ProGuard to obfuscate the compiled code, making it harder to reverse engineer.

## Building Obfuscated JAR

To build an obfuscated version of your plugin, run:

```bash
./gradlew obfuscate
```

Or on Windows:
```bash
gradlew.bat obfuscate
```

The obfuscated JAR will be created at:
```
build/libs/AllyLocator-<version>-obfuscated.jar
```

## What Gets Obfuscated

- **Class names**: Renamed to short, meaningless names (e.g., `a`, `b`, `c`)
- **Method names**: Renamed to short names (except public API methods)
- **Variable names**: Renamed to short names
- **Package structure**: Flattened and renamed

## What Stays Readable

The following are kept readable (as defined in `proguard-rules.pro`):
- Main plugin class (`com.allylocator.Main`)
- Public API methods (setup, start, shutdown)
- Event listener methods
- Classes accessed via reflection (PartyService, etc.)

## Mapping File

A mapping file is generated at `build/proguard-mapping.txt` that shows:
- Original class/method names → Obfuscated names

**Important**: Keep this file secure if you need to debug obfuscated code. Don't commit it to version control.

## Customization

Edit `proguard-rules.pro` to:
- Keep additional classes/methods readable
- Adjust obfuscation aggressiveness
- Remove logging statements (uncomment the `-assumenosideeffects` rule)

## Testing

Always test the obfuscated JAR before distribution:
1. Build: `./gradlew obfuscate`
2. Copy the obfuscated JAR to your Hytale server
3. Test all plugin functionality
4. Verify party/faction detection still works

## Notes

- The obfuscated JAR is larger than the original due to ProGuard metadata
- Stack traces will show obfuscated names (use mapping file to decode)
- Reflection-based code (like PartyService) is kept readable to ensure compatibility
