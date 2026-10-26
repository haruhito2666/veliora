let counter = 0;

export function newId(prefix) {
  counter += 1;
  const time = Date.now().toString(36);
  const count = counter.toString(36);
  const noise = Math.random().toString(36).slice(2, 8);
  return `${prefix}_${time}${count}${noise}`;
}

export function nowISO() {
  return new Date().toISOString();
}

export function sleep(ms) {
  if (!ms) return Promise.resolve();
  return new Promise((resolve) => setTimeout(resolve, ms));
}
