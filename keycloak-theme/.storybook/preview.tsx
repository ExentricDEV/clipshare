import React from "react";
import type { Preview } from "@storybook/react";

const preview: Preview = {
    parameters: {
        controls: {
            matchers: {
                color: /(background|color)$/i,
                date: /Date$/i
            }
        }
    },
    decorators: [
        Story => {
            return (
                <div
                    onSubmit={e => {
                        e.preventDefault();
                        console.log("Submit prevented globally (Storybook)");
                    }}
                >
                    <Story />
                </div>
            );
        }
    ]
};

export default preview;
