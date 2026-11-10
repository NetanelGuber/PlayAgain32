//! Only an explicit, valid per-game rate may change original frame timing.

pub fn requested_rate(value: Option<&str>) -> Option<f32> {
    let fps = value?.trim().parse::<f64>().ok()?;
    if !fps.is_finite() || fps <= 1.0 {
        return None;
    }
    // Retain the existing whole-refresh cadence for explicit overrides.
    let refreshes = (120.0 / fps - 0.01).ceil().max(1.0);
    Some((120.0 / refreshes) as f32)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn absent_disabled_or_invalid_values_preserve_original_timing() {
        for value in [None, Some(""), Some("0"), Some(" 0 "), Some("junk"),
            Some("-60"), Some("1"), Some("NaN"), Some("inf"), Some("1e999")] {
            assert_eq!(requested_rate(value), None, "{value:?}");
        }
    }

    #[test]
    fn explicit_rates_keep_the_existing_cadence_and_upper_bound() {
        assert_eq!(requested_rate(Some(" 60 ")), Some(60.0));
        assert_eq!(requested_rate(Some("30")), Some(30.0));
        assert_eq!(requested_rate(Some("50")), Some(40.0));
        assert_eq!(requested_rate(Some("120")), Some(120.0));
        assert_eq!(requested_rate(Some("240")), Some(120.0));
    }
}
