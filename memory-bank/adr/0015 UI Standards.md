# ADR 0015: UI Typography Standards

**Status:** Accepted  
**Date:** 2026-04-09  
**Decision Maker:** Design System Team  
**Category:** User Interface / Design System

---

## Context

Reveila uses multiple UI platforms:
- React Native/Expo for cross-platform mobile
- Native Android (Kotlin/Compose) for platform-specific features

Prior to this ADR, font usage was inconsistent across platforms with:
- Body text varying from 12-16px
- Button text ranging from 13-15px with inconsistent weights (700-800)
- Titles varying from 14-32px 
- No standardized design system
- Components not using theme definitions

This inconsistency created a fragmented user experience and made the application appear less professional.

---

## Decision

We will adopt a comprehensive typography specification that defines 14 standard text styles to be used consistently across all Reveila platforms.

### Design Principles

1. **Consistency**: Same visual hierarchy across all platforms
2. **Readability**: Optimal font sizes for mobile devices
3. **Accessibility**: Clear contrast and appropriate line heights
4. **Professionalism**: Balanced, modern typography

### Font Family

| Platform | Font Family |
|----------|-------------|
| React Native | System Default (San Francisco on iOS, Roboto on Android) |
| Native Android | `FontFamily.Default` (Roboto) |

---

## Standard Typography Scale

### 1. Display Heading (Hero)
**Usage:** Large page titles, splash screens, major section headers

| Property | Value |
|----------|-------|
| Font Size | 28px / 28sp |
| Line Height | 36px / 36sp |
| Font Weight | Bold / 700 |
| Letter Spacing | 0px |

**Example:** "Building Sovereign Memory...", App title on launch

---

### 2. Page Title
**Usage:** Screen titles, primary headings

| Property | Value |
|----------|-------|
| Font Size | 20px / 20sp |
| Line Height | 28px / 28sp |
| Font Weight | Bold / 700 |
| Letter Spacing | 0px |

**Example:** "Set Up Knowledge Vault", "Settings", "Chat"

---

### 3. Section Heading
**Usage:** Card titles, subsection headers

| Property | Value |
|----------|-------|
| Font Size | 16px / 16sp |
| Line Height | 24px / 24sp |
| Font Weight | SemiBold / 600 |
| Letter Spacing | 0px |

**Example:** "Priority Keywords", "Master Password", "AI Configuration"

---

### 4. Body Text (Default)
**Usage:** Main content, descriptions, paragraphs

| Property | Value |
|----------|-------|
| Font Size | 14px / 14sp |
| Line Height | 22px / 22sp |
| Font Weight | Normal / 400 |
| Letter Spacing | 0px |

**Example:** Long-form descriptions, help text, content blocks

---

### 5. Body Text (Compact)
**Usage:** Compact descriptions, list items, secondary content

| Property | Value |
|----------|-------|
| Font Size | 13px / 13sp |
| Line Height | 20px / 20sp |
| Font Weight | Normal / 400 |
| Letter Spacing | 0px |

**Example:** Settings descriptions, table rows, card details

---

### 6. Small Text
**Usage:** Captions, metadata, timestamps, disclaimers

| Property | Value |
|----------|-------|
| Font Size | 12px / 12sp |
| Line Height | 18px / 18sp |
| Font Weight | Normal / 400 |
| Letter Spacing | 0px |

**Example:** "Last updated 5 mins ago", input placeholders, fine print

---

### 7. Micro Text
**Usage:** Badges, tags, ultra-compact labels

| Property | Value |
|----------|-------|
| Font Size | 10px / 10sp |
| Line Height | 16px / 16sp |
| Font Weight | Bold / 700 |
| Letter Spacing | 0.5px / 0.5sp |

**Example:** Status badges ("LOCAL", "CLOUD"), chip labels, counters

---

### 8. Input Field Text
**Usage:** Text inside input fields

| Property | Value |
|----------|-------|
| Font Size | 16px / 16sp |
| Line Height | 24px / 24sp |
| Font Weight | Normal / 400 |
| Letter Spacing | 0px |

**Example:** Password inputs, text fields, search bars

---

### 9. Input Field Label
**Usage:** Labels above/beside input fields

| Property | Value |
|----------|-------|
| Font Size | 14px / 14sp |
| Line Height | 20px / 20sp |
| Font Weight | Medium / 500 |
| Letter Spacing | 0px |

**Example:** "Master Password (16-32 chars)", "Priority Keywords"

---

### 10. Button Text
**Usage:** All button labels

| Property | Value |
|----------|-------|
| Font Size | 14px / 14sp |
| Line Height | 20px / 20sp |
| Font Weight | Bold / 700 |
| Letter Spacing | 0.5px / 0.5sp |

**Example:** "Start Secure Indexing", "Go", "Open Directory Picker"

---

### 11. Button Text (Small)
**Usage:** Compact buttons, secondary actions

| Property | Value |
|----------|-------|
| Font Size | 13px / 13sp |
| Line Height | 18px / 18sp |
| Font Weight | Bold / 700 |
| Letter Spacing | 0.5px / 0.5sp |

**Example:** "REFRESH", "+ New Task", link buttons

---

### 12. Tab/Navigation Text
**Usage:** Tab bar labels, navigation items

| Property | Value |
|----------|-------|
| Font Size | 13px / 13sp |
| Line Height | 18px / 18sp |
| Font Weight | Bold / 700 |
| Letter Spacing | 0.5px / 0.5sp |

**Example:** "Settings", "Chat", "Explorer" tabs

---

### 13. Warning/Error Text
**Usage:** Warning messages, error states, critical information

| Property | Value |
|----------|-------|
| Font Size | 12px / 12sp |
| Line Height | 18px / 18sp |
| Font Weight | SemiBold / 600 |
| Letter Spacing | 0px |

**Example:** Password warnings, security notices, validation errors

---

### 14. Monospace/Code Text
**Usage:** Code snippets, logs, technical output

| Property | Value |
|----------|-------|
| Font Size | 12px / 12sp |
| Line Height | 18px / 18sp |
| Font Weight | Normal / 400 |
| Font Family | Monospace / FontFamily.Monospace |
| Letter Spacing | 0px |

**Example:** Discovery logs, configuration values, technical details

---

## Implementation

### React Native Typography

**File:** `apps/expo/Reveila/components/themed-text.tsx`

```typescript
const styles = StyleSheet.create({
  displayHeading: {
    fontSize: 28,
    lineHeight: 36,
    fontWeight: '700',
  },
  pageTitle: {
    fontSize: 20,
    lineHeight: 28,
    fontWeight: '700',
  },
  sectionHeading: {
    fontSize: 16,
    lineHeight: 24,
    fontWeight: '600',
  },
  bodyDefault: {
    fontSize: 14,
    lineHeight: 22,
    fontWeight: '400',
  },
  bodyCompact: {
    fontSize: 13,
    lineHeight: 20,
    fontWeight: '400',
  },
  smallText: {
    fontSize: 12,
    lineHeight: 18,
    fontWeight: '400',
  },
  microText: {
    fontSize: 10,
    lineHeight: 16,
    fontWeight: '700',
    letterSpacing: 0.5,
  },
  inputText: {
    fontSize: 16,
    lineHeight: 24,
    fontWeight: '400',
  },
  inputLabel: {
    fontSize: 14,
    lineHeight: 20,
    fontWeight: '500',
  },
  buttonText: {
    fontSize: 14,
    lineHeight: 20,
    fontWeight: '700',
    letterSpacing: 0.5,
  },
  buttonTextSmall: {
    fontSize: 13,
    lineHeight: 18,
    fontWeight: '700',
    letterSpacing: 0.5,
  },
  tabText: {
    fontSize: 13,
    lineHeight: 18,
    fontWeight: '700',
    letterSpacing: 0.5,
  },
  warningText: {
    fontSize: 12,
    lineHeight: 18,
    fontWeight: '600',
  },
  monospaceText: {
    fontSize: 12,
    lineHeight: 18,
    fontWeight: '400',
    fontFamily: 'monospace',
  },
});
```

### Native Android Typography

**File:** `android/src/main/kotlin/com/reveila/android/ui/theme/Type.kt`

```kotlin
val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        letterSpacing =0.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.5.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
```

---

## Font Weight Reference

| Name | React Native | Android | Numeric |
|------|--------------|---------|---------|
| Normal | '400' | FontWeight.Normal | 400 |
| Medium | '500' | FontWeight.Medium | 500 |
| SemiBold | '600' | FontWeight.SemiBold | 600 |
| Bold | '700' | FontWeight.Bold | 700 |
| ExtraBold | '800' | FontWeight.ExtraBold | 800 |

---

## Migration Checklist

### Phase 1: Update Theme Files ✅
- [x] Update `apps/expo/Reveila/components/themed-text.tsx`
- [x] Update `android/src/main/kotlin/com/reveila/android/ui/theme/Type.kt`

### Phase 2: Update React Native Components
- [ ] `app/(tabs)/index.tsx`
- [ ] `app/(tabs)/settings.tsx`
- [ ] `app/(tabs)/explore.tsx`
- [ ] `components/MasterPasswordSetup.tsx`
- [ ] `components/LockScreen.tsx`
- [ ] `components/ChangePasswordModal.tsx`

### Phase 3: Update Native Android Components
- [ ] `ui/components/SovereignOnboardingScreen.kt`
- [ ] `ui/components/FirstRunDialog.kt`
- [ ] All other native screens

### Phase 4: Testing
- [ ] Visual regression testing on both platforms
- [ ] Accessibility testing (font scaling)
- [ ] Cross-platform consistency verification

---

## Consequences

### Positive

1. **Visual Consistency**: Unified appearance across all platforms
2. **Professional Appearance**: Cohesive, well-designed typography hierarchy
3. **Improved Readability**: Optimized font sizes and line heights for mobile
4. **Easier Development**: Clear guidelines reduce decision-making overhead
5. **Better Maintenance**: Centralized design system simplifies updates
6. **Accessibility**: Standardized sizes work better with system font scaling
7. **Foundation for Design Tokens**: Establishes pattern for future design system expansion

### Negative

1. **Migration Effort**: Requires updating 30+ UI components across platforms
2. **Breaking Changes**: May require visual regression testing
3. **Learning Curve**: Team needs to learn new style names
4. **Rigid Structure**: Less flexibility for one-off designs

### Neutral

1. **Design System Maturity**: Establishes foundation for future design tokens
2. **Documentation**: Requires ongoing maintenance of standards
3. **Enforcement**: Needs code review process to ensure compliance

---

## Special Considerations

### Monospace Text
Used for code, logs, and technical content. Always use native monospace fonts.

### Dynamic Font Sizing
All font sizes should respect system accessibility settings. Use `sp` units on Android and default React Native text sizing behavior.

### Line Height Optimization
Line heights are optimized for readability on mobile screens. Maintain the specified line heights to ensure proper text flow and avoid cramped or loose appearance.

---

## Compliance & Enforcement

All new UI components must adhere to this specification. Code review must verify:

1. Use of standardized font sizes from theme
2. Correct font weights
3. Proper line heights
4. Consistent letter spacing where specified
5. No inline font styles (use theme styles)

---

## Related ADRs

- None (this is the first UI standards ADR)

---

## References

- Material Design Typography System
- iOS Human Interface Guidelines - Typography
- Web Content Accessibility Guidelines (WCAG) 2.1
- React Native TextStyle Documentation
- Jetpack Compose Material3 Typography

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-04-09 | Initial specification created and theme files updated |
