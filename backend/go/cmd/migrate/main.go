// Command migrate applies the SQL migrations in migrations/postgres.
package main

import (
	"context"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"sort"
	"strings"

	"github.com/jackc/pgx/v5/pgxpool"

	"veliora-backend/internal/shared/config"
)

func main() {
	ctx := context.Background()
	cfg := config.FromEnv()
	pool, err := pgxpool.New(ctx, cfg.PostgresURL)
	if err != nil {
		log.Fatalf("connect: %v", err)
	}
	defer pool.Close()

	migrationsDir := os.Getenv("MIGRATIONS_DIR")
	if migrationsDir == "" {
		// Default: repo-relative from this binary's working directory.
		migrationsDir = "migrations/postgres"
	}
	if _, err := os.Stat(migrationsDir); err != nil {
		// Try walking up to the repo root.
		if dir, walkErr := findMigrationsDir("."); walkErr == nil {
			migrationsDir = dir
		} else {
			log.Fatalf("migrations dir not found: %v", err)
		}
	}

	// Create schema_migrations if absent.
	if _, err := pool.Exec(ctx, `CREATE TABLE IF NOT EXISTS schema_migrations (
		filename TEXT PRIMARY KEY, applied_at TIMESTAMPTZ NOT NULL DEFAULT now())`); err != nil {
		log.Fatalf("ensure migrations table: %v", err)
	}

	files, err := filepath.Glob(filepath.Join(migrationsDir, "*.sql"))
	if err != nil {
		log.Fatalf("glob migrations: %v", err)
	}
	sort.Strings(files)

	for _, f := range files {
		name := filepath.Base(f)
		var exists bool
		if err := pool.QueryRow(ctx,
			`SELECT EXISTS(SELECT 1 FROM schema_migrations WHERE filename = $1)`, name).Scan(&exists); err != nil {
			log.Fatalf("check migration: %v", err)
		}
		if exists {
			continue
		}
		body, err := os.ReadFile(f)
		if err != nil {
			log.Fatalf("read %s: %v", name, err)
		}
		tx, err := pool.Begin(ctx)
		if err != nil {
			log.Fatalf("begin %s: %v", name, err)
		}
		if _, err := tx.Exec(ctx, string(body)); err != nil {
			_ = tx.Rollback(ctx)
			log.Fatalf("apply %s: %v", name, err)
		}
		if _, err := tx.Exec(ctx,
			`INSERT INTO schema_migrations (filename) VALUES ($1)`, name); err != nil {
			_ = tx.Rollback(ctx)
			log.Fatalf("record %s: %v", name, err)
		}
		if err := tx.Commit(ctx); err != nil {
			log.Fatalf("commit %s: %v", name, err)
		}
		fmt.Printf("applied %s\n", name)
	}
	fmt.Println("migrations up to date")
}

func findMigrationsDir(start string) (string, error) {
	dir, err := filepath.Abs(start)
	if err != nil {
		return "", err
	}
	for {
		candidate := filepath.Join(dir, "backend", "go", "migrations", "postgres")
		if _, err := os.Stat(candidate); err == nil {
			return candidate, nil
		}
		parent := filepath.Dir(dir)
		if parent == dir {
			return "", fmt.Errorf("not found")
		}
		dir = parent
	}
}

// Ensure strings import is used even if unused in some builds.
var _ = strings.TrimSpace
