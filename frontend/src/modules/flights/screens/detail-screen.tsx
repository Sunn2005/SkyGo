import { Redirect } from "expo-router";

// Older links return to the current flight list; selection is handled there.
export default function DetailScreen() {
  return <Redirect href="/results" />;
}
