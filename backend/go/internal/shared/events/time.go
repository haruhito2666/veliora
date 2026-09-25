package events

import (
	"time"
)

// parseTime parses RFC3339 timestamps produced by Postgres timestamptz.
func parseTime(s string) (time.Time, error) {
	for _, layout := range []string{time.RFC3339Nano, time.RFC3339} {
		if t, err := time.Parse(layout, s); err == nil {
			return t, nil
		}
	}
	return time.Time{}, &time.ParseError{Layout: "RFC3339", Value: s}
}
