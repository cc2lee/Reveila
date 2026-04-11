import { StyleSheet, Text, type TextProps } from 'react-native';

import { useThemeColor } from '@/hooks/use-theme-color';

export type ThemedTextProps = TextProps & {
  lightColor?: string;
  darkColor?: string;
  type?: 'default' | 'title' | 'defaultSemiBold' | 'subtitle' | 'link';
};

export function ThemedText({
  style,
  lightColor,
  darkColor,
  type = 'default',
  ...rest
}: ThemedTextProps) {
  const color = useThemeColor({ light: lightColor, dark: darkColor }, 'text');

  return (
    <Text
      style={[
        { color },
        type === 'default' ? styles.default : undefined,
        type === 'title' ? styles.title : undefined,
        type === 'defaultSemiBold' ? styles.defaultSemiBold : undefined,
        type === 'subtitle' ? styles.subtitle : undefined,
        type === 'link' ? styles.link : undefined,
        style,
      ]}
      {...rest}
    />
  );
}

const styles = StyleSheet.create({
  default: {
    fontSize: 14,
    lineHeight: 22,
    fontWeight: '400',
  },
  defaultSemiBold: {
    fontSize: 16,
    lineHeight: 24,
    fontWeight: '600',
  },
  title: {
    fontSize: 28,
    fontWeight: '700',
    lineHeight: 36,
  },
  subtitle: {
    fontSize: 20,
    fontWeight: '700',
    lineHeight: 28,
  },
  link: {
    fontSize: 14,
    lineHeight: 22,
    fontWeight: '400',
    color: '#0a7ea4',
  },
  // Additional standardized styles
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
